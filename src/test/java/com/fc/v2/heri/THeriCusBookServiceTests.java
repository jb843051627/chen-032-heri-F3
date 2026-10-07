package com.fc.v2.heri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THeriCusBook;
import com.fc.v2.model.auto.THeriCusRow;
import com.fc.v2.model.custom.CusDoorReject;
import com.fc.v2.model.custom.CusIntakeCmd;
import com.fc.v2.model.custom.CusIntakeReceipt;
import com.fc.v2.model.custom.CusRowInput;
import com.fc.v2.model.custom.CusRowTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报关行整串进门规矩集成测试（H2 + 真实 mapper）。
 *
 * @author fuce
 * @date 2026-10-07
 */
class THeriCusBookServiceTests extends CusBookITSupport {

    private static final String OP = "agent_wang";
    private static final String BIZ = "ML-20261007-01";
    private static final String SHIP_AGENT = "船代";
    private static final String PLATFORM = "平台";

    @BeforeEach
    void init() throws Exception {
        setUp();
    }

    // ---------------- 进门的样子：整串、串头、溢出另开 ----------------

    @Test
    @DisplayName("整串进门：行次册上排 1..N，件数齐且相等全签下，三笔签+退=进门，出收讫句")
    void firstIntake_allSigned() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E1", "10", "10", "20GP"),
                line("E2", "20", "20", "40GP"),
                line("E3", "30", "30", "40HQ")));

        assertTrue(rc.getBookNo().startsWith("C"));
        assertEquals(Integer.valueOf(3), rc.getDeclaredRows());
        assertEquals(Integer.valueOf(3), rc.getInRows());
        assertEquals(Integer.valueOf(3), rc.getSignedRows());
        assertEquals(Integer.valueOf(0), rc.getBackRows());
        assertEquals(0, rc.getDoorRejects().size());
        assertTrue(rc.getAcceptedText().contains("收讫"), "头一趟非空出收讫");
        assertFalse(rc.isFaceOnly());

        List<THeriCusRow> saved = cusBookService.listRows(rc.getBookNo());
        assertEquals(3, saved.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(Integer.valueOf(i + 1), saved.get(i).getRowNo(), "行次册上连号排");
            assertEquals(Integer.valueOf(1), saved.get(i).getStatus(), "全签");
            assertEquals(Integer.valueOf(1), saved.get(i).getIntakeSeq());
        }
        assertEquals(1, cusBookService.selectByBookNo(rc.getBookNo()).getStatus(), "全签=封住");
    }

    @Test
    @DisplayName("串头那格空着：按 150 行算，原样记在头表，事后查得回")
    void declaredBlank_defaults150() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "  ", rows(line("E1", "1", "1", "20GP")));
        assertEquals(Integer.valueOf(150), rc.getDeclaredRows());
        assertEquals(Integer.valueOf(150),
                cusBookService.selectByBookNo(rc.getBookNo()).getDeclareRows());
    }

    @Test
    @DisplayName("串头只认行数：数字之外的写法整串不收（不能拿别的字符凑）")
    void declaredGarbage_rejected() {
        assertThrows(IllegalArgumentException.class, () -> intake(BIZ, SHIP_AGENT, "150行",
                rows(line("E1", "1", "1", "20GP"))));
        assertEquals(0, bookMapper.selectCount(new QueryWrapper<THeriCusBook>()));
    }

    @Test
    @DisplayName("串头写 2 行、交 3 行：只当 2 行，多的 1 行不就地往下接，回执点出溢出")
    void overflow_carriedToAnotherBook() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "2", rows(
                line("E1", "1", "1", "20GP"),
                line("E2", "2", "2", "20GP"),
                line("E3", "3", "3", "20GP")));

        assertEquals(Integer.valueOf(1), rc.getOverflowRows(), "多出 1 行另开一串");
        assertEquals(Integer.valueOf(2), rc.getInRows());
        List<THeriCusRow> saved = cusBookService.listRows(rc.getBookNo());
        assertEquals(2, saved.size());
        assertEquals("E2", saved.get(1).getEntryId());
        assertFalse(saved.stream().anyMatch(r -> "E3".equals(r.getEntryId())),
                "第三行不许就地接到本串");
    }

    // ---------------- 解析只跑一趟：箱型门外核 ----------------

    @Test
    @DisplayName("箱型不合的行拦在门外不落册；箱型空着按没写，不当都可以")
    void badKind_stoppedAtDoor() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "4", rows(
                line("E1", "1", "1", "20GP"),
                line("E2", "1", "1", "99XX"),     // 白名单没有
                line("E3", "1", "1", null),       // 空=没写
                line("E4", "1", "1", "45HQ")));   // 已撤下，照样不合

        assertEquals(3, rc.getDoorRejects().size());
        // 门外行按整串次序点名，原因点得明白
        List<Integer> lines = new ArrayList<Integer>();
        for (CusDoorReject d : rc.getDoorRejects()) {
            lines.add(d.getSheetLine());
            assertNotNull(d.getReason());
        }
        assertTrue(lines.contains(2));
        assertTrue(lines.contains(3));
        assertTrue(lines.contains(4));
        assertTrue(rc.getDoorRejects().get(1).getReason().contains("没写"));

        List<THeriCusRow> saved = cusBookService.listRows(rc.getBookNo());
        assertEquals(1, saved.size(), "门外一律不落册");
        assertEquals(Integer.valueOf(1), saved.get(0).getRowNo(), "门外行不占行次");
        assertEquals(Integer.valueOf(1), rc.getInRows(), "门内只 1 行");
        assertEquals(Integer.valueOf(1), rc.getSignedRows());
        assertEquals(Integer.valueOf(0), rc.getBackRows());
        assertTrue(rc.getAcceptedText().contains("进门 1 行"), "有一行进门就收讫这一行");
        assertEquals(0, cusBookService.selectByBookNo(rc.getBookNo()).getStatus(),
                "申报 4 行只落 1 行，册子不封，等续办");
    }

    @Test
    @DisplayName("整串全被门外拦回：一行不落册、三笔全 0、收讫不出、册子开着")
    void allStoppedAtDoor_noReceived() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "2", rows(
                line("E1", "1", "1", null),
                line("E2", "1", "1", "99XX")));

        assertEquals(2, rc.getDoorRejects().size());
        assertEquals(Integer.valueOf(0), rc.getInRows());
        assertEquals(Integer.valueOf(0), rc.getSignedRows());
        assertEquals(Integer.valueOf(0), rc.getBackRows());
        assertNull(rc.getAcceptedText(), "没有一行进门，收讫句不出");
        assertEquals(Integer.valueOf(0),
                cusBookService.selectByBookNo(rc.getBookNo()).getReceivedFlag());
        assertEquals(0, cusBookService.listRows(rc.getBookNo()).size());
    }

    @Test
    @DisplayName("报关单号空着、同串单号重复：认不住行，拦在门外")
    void missingOrDupEntry_stoppedAtDoor() {
        List<CusRowInput> rows = rows(
                line(null, "1", "1", "20GP"),
                line("E2", "1", "1", "20GP"),
                line("E2", "2", "2", "20GP"));
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "3", rows);

        assertEquals(2, rc.getDoorRejects().size());
        assertEquals(1, cusBookService.listRows(rc.getBookNo()).size());
    }

    // ---------------- 每件数：缺一不办、不等退且写差异 ----------------

    @Test
    @DisplayName("申报件数缺 / 实签件数缺：不办也不当零，行退回在册上写明")
    void qtyMissing_rowBackNotZero() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E1", null, "10", "20GP"),
                line("E2", "10", null, "20GP"),
                line("E3", "10", "abc", "20GP")));

        assertEquals(Integer.valueOf(3), rc.getInRows(), "都进门了（箱型合）");
        assertEquals(Integer.valueOf(0), rc.getSignedRows());
        assertEquals(Integer.valueOf(3), rc.getBackRows());
        assertEquals(Integer.valueOf(3), rc.getInRows(), "签+退=进门");

        List<THeriCusRow> saved = cusBookService.listRows(rc.getBookNo());
        assertTrue(saved.get(0).getBackReason().contains("申报件数没写"));
        assertNull(saved.get(0).getDeclareQty(), "缺的不当零，库里也是空");
        assertTrue(saved.get(1).getBackReason().contains("实签件数没写"));
        assertNull(saved.get(1).getSignedQty());
        assertTrue(saved.get(2).getBackReason().contains("不是数"));
    }

    @Test
    @DisplayName("件数不等就退：差异逐行写得出（少 2 件 / 多 1 件）")
    void qtyMismatch_writesDiff() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "2", rows(
                line("E1", "10", "8", "20GP"),   // 实签比申报少 2
                line("E2", "10", "11", "20GP"))); // 多 1

        List<THeriCusRow> saved = cusBookService.listRows(rc.getBookNo());
        assertEquals("少 2 件", saved.get(0).getBackReason());
        assertEquals("多 1 件", saved.get(1).getBackReason());
        assertEquals(Integer.valueOf(0), rc.getSignedRows());
        assertEquals(Integer.valueOf(2), rc.getBackRows());
    }

    @Test
    @DisplayName("一行不成不抹整串：签的在册、退的走它自己，三笔仍旧守恒")
    void partialKeepsBookIntact() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E1", "10", "10", "20GP"),
                line("E2", "10", "9", "20GP"),
                line("E3", "20", "20", "40HQ")));

        assertEquals(Integer.valueOf(3), rc.getInRows());
        assertEquals(Integer.valueOf(2), rc.getSignedRows());
        assertEquals(Integer.valueOf(1), rc.getBackRows());
        List<THeriCusRow> saved = cusBookService.listRows(rc.getBookNo());
        assertEquals(Integer.valueOf(1), saved.get(0).getStatus());
        assertEquals(Integer.valueOf(2), saved.get(1).getStatus());
        assertEquals(Integer.valueOf(1), saved.get(2).getStatus());
        assertEquals(0, cusBookService.selectByBookNo(rc.getBookNo()).getStatus(), "有退回=在册留续办");
    }

    // ---------------- 重复进门：只照面，不翻账（去年那档事） ----------------

    @Test
    @DisplayName("同串第二遍：已签行不回待签、不另起记录、内容不覆盖，不出收讫句")
    void secondIntake_faceOnly_doesNotTouchSigned() {
        // 串头报 3 行、头一趟先到 2 行，还欠 1 个名额给续办补
        CusIntakeReceipt first = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E1", "10", "10", "20GP"),
                line("E2", "20", "20", "20GP")));
        String bookNo = first.getBookNo();
        THeriCusRow row1Before = cusBookService.findRow(bookNo, 1);

        // 第二遍：把已签的行改成“对不上”，还多带一行——已签账一律不许动
        CusIntakeReceipt second = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E1", "10", "7", "20GP"),    // 想冲成退回？不许
                line("E2", "20", null, "20GP"),   // 想冲成待办？不许
                line("E9", "1", "1", "20GP")));   // 新行：续办补在后面

        assertTrue(second.isFaceOnly());
        assertNull(second.getAcceptedText(), "照面不出收讫句");
        assertEquals(Integer.valueOf(3), second.getBookInRows(), "补上欠的名额后头表进门 3 行");

        THeriCusRow row1After = cusBookService.findRow(bookNo, 1);
        assertEquals(Integer.valueOf(1), row1After.getStatus(), "已签就是已签，不回待签/退回");
        assertEquals(0, row1After.getSignedQty().compareTo(new java.math.BigDecimal("10")),
                "第二遍的数不许覆盖第一遍的账");
        assertEquals(row1Before.getCreateTime(), row1After.getCreateTime());
        assertEquals(1, bookMapper.selectCount(new QueryWrapper<THeriCusBook>()
                .eq("biz_no", BIZ).eq("source", SHIP_AGENT)), "不另起一册");
        assertEquals(3, cusBookService.listRows(bookNo).size(), "不另起重复记录，只补新行");
    }

    @Test
    @DisplayName("船代和平台同一张单各录一次：两串都留，各是各的号、互不翻账")
    void twoSources_twoBooks() {
        CusIntakeReceipt a = intake(BIZ, SHIP_AGENT, "1", rows(line("E1", "10", "9", "20GP")));
        CusIntakeReceipt b = intake(BIZ, PLATFORM, "1", rows(line("E1", "10", "10", "20GP")));

        assertNotEquals(a.getBookNo(), b.getBookNo());
        assertEquals(2, bookMapper.selectCount(new QueryWrapper<THeriCusBook>().eq("biz_no", BIZ)));
        // 平台那串签了，船代那串退着，互不影响
        assertEquals(Integer.valueOf(0), cusBookService.selectByBookNo(a.getBookNo()).getSignedRows());
        assertEquals(Integer.valueOf(1), cusBookService.selectByBookNo(b.getBookNo()).getSignedRows());
    }

    // ---------------- 行次：系统排，退了再报认原号，续办只补没办的 ----------------

    @Test
    @DisplayName("续办：退回行修正后还是当初那个行号转签；已签的不惊动；新行往后排")
    void continuation_keepsRowNoAndFillsOnlyBack() {
        CusIntakeReceipt first = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E1", "10", "10", "20GP"),   // 行1 已签
                line("E2", "10", "8", "20GP"),    // 行2 退回（少2件）
                line("E3", "20", "19", "40HQ"))); // 行3 退回（少1件）

        String bookNo = first.getBookNo();
        assertEquals(Integer.valueOf(1), cusBookService.findRow(bookNo, 1).getStatus());

        // 隔天续办：行2 改齐、行3 仍不齐；把行1 也带上（照面）
        CusIntakeReceipt again = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E1", "10", "10", "20GP"),
                line("E2", "10", "10", "20GP"),
                line("E3", "20", "18", "40HQ")));

        assertTrue(again.isFaceOnly());
        assertEquals(Integer.valueOf(2), again.getInRows(), "本趟定去向的=2 行续办；E1只照面不计");
        assertEquals(Integer.valueOf(1), again.getSignedRows());
        assertEquals(Integer.valueOf(1), again.getBackRows());

        List<THeriCusRow> saved = cusBookService.listRows(bookNo);
        assertEquals(3, saved.size());
        assertEquals(Integer.valueOf(1), saved.get(0).getRowNo());
        assertEquals(Integer.valueOf(2), saved.get(1).getRowNo(), "退了再报还是当初那个号");
        assertEquals(Integer.valueOf(1), saved.get(1).getStatus(), "行2 转签");
        assertEquals(Integer.valueOf(2), saved.get(2).getStatus(), "行3 仍退回");
        assertEquals("少 2 件", saved.get(2).getBackReason(), "原因更新成本趟差异");
        assertEquals(Integer.valueOf(2), saved.get(1).getIntakeSeq(), "行2 是第二趟办的");
        assertEquals(Integer.valueOf(1), saved.get(0).getIntakeSeq(), "已签行不惊动，趟次不变");

        THeriCusBook head = cusBookService.selectByBookNo(bookNo);
        assertEquals(Integer.valueOf(3), head.getInRows());
        assertEquals(Integer.valueOf(2), head.getSignedRows());
        assertEquals(Integer.valueOf(1), head.getBackRows(), "册上三笔守恒");
        assertEquals(Integer.valueOf(3), head.getDeclareRows(), "头一趟原报行数不改口");
    }

    @Test
    @DisplayName("续办补新行只补到头一趟申报行数；再往后的行溢出另开一串，不扩本串")
    void continuation_newRowsRespectOriginalCapacity() {
        // 头一趟串头报 3 行：1 行签、1 行被门外拦回（不落册）、1 行退回 → 册上 2 行，还欠 1 个名额
        CusIntakeReceipt first = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E1", "10", "10", "20GP"),
                line("E2", "1", "1", "99XX"),    // 门外，不落册
                line("E3", "20", "19", "40HQ"))); // 退回
        String bookNo = first.getBookNo();
        assertEquals(2, cusBookService.listRows(bookNo).size());

        // 续办：E4 补最后一个名额（行次 3），E5 已超容量，溢出另开一串
        CusIntakeReceipt again = intake(BIZ, SHIP_AGENT, "3", rows(
                line("E4", "5", "5", "20GP"),
                line("E5", "6", "6", "20GP")));

        assertEquals(Integer.valueOf(1), again.getOverflowRows(), "E5 不许就地接进本串");
        List<THeriCusRow> saved = cusBookService.listRows(bookNo);
        assertEquals(3, saved.size());
        assertEquals(Integer.valueOf(3), saved.get(2).getRowNo(), "E4 补的是本串欠的名额");
        assertEquals("E4", saved.get(2).getEntryId());
        assertFalse(saved.stream().anyMatch(r -> "E5".equals(r.getEntryId())));
    }

    @Test
    @DisplayName("续办只报已签行：纯照面，本趟三笔全 0，账一根毫毛不动")
    void continuationOnlySigned_pureFace() {
        CusIntakeReceipt first = intake(BIZ, SHIP_AGENT, "1", rows(line("E1", "10", "10", "20GP")));
        CusIntakeReceipt again = intake(BIZ, SHIP_AGENT, "1", rows(line("E1", "10", "1", "20GP")));

        assertEquals(Integer.valueOf(0), again.getInRows());
        assertEquals(Integer.valueOf(0), again.getSignedRows());
        assertEquals(Integer.valueOf(0), again.getBackRows());
        THeriCusRow r = cusBookService.findRow(first.getBookNo(), 1);
        assertEquals(Integer.valueOf(1), r.getStatus());
        assertEquals(0, r.getSignedQty().compareTo(new java.math.BigDecimal("10")));
    }

    @Test
    @DisplayName("行不会飘到别的串：续办行只回本串，别串拉不来一行")
    void rowsNeverCrossBooks() {
        CusIntakeReceipt a = intake(BIZ, SHIP_AGENT, "1", new ArrayList<CusRowInput>()); // 空册 A
        CusIntakeReceipt b = intake("ML-OTHER", SHIP_AGENT, "1", rows(line("E1", "1", "1", "20GP")));

        assertEquals(0, cusBookService.listRows(a.getBookNo()).size(), "空册就是空的，不从别串补");
        assertEquals(1, cusBookService.listRows(b.getBookNo()).size());
        // 拿 B 的行去敲 A 的续办：A 里行落 A 自己（新行），B 的行不动
        CusIntakeReceipt a2 = intake(BIZ, SHIP_AGENT, "1", rows(line("E1", "2", "2", "40GP")));
        assertEquals(1, cusBookService.listRows(a.getBookNo()).size());
        assertEquals("40GP", cusBookService.listRows(a.getBookNo()).get(0).getDeviceKind());
        assertEquals("20GP", cusBookService.listRows(b.getBookNo()).get(0).getDeviceKind());
        assertEquals(1, cusBookService.listRows(b.getBookNo()).get(0).getStatus());
        assertTrue(a2.isFaceOnly());
    }

    // ---------------- 空串是正经输入 ----------------

    @Test
    @DisplayName("空串：册子照立、原报行数在册，回执只写空册、收讫句不出")
    void emptyBook_isValid() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, null, new ArrayList<CusRowInput>());

        assertTrue(rc.isEmptyBook());
        assertNull(rc.getAcceptedText());
        assertEquals(Integer.valueOf(150), rc.getDeclaredRows());
        assertEquals(Integer.valueOf(0), rc.getInRows());
        THeriCusBook head = cusBookService.selectByBookNo(rc.getBookNo());
        assertNotNull(head);
        assertEquals(Integer.valueOf(0), head.getInRows());
        assertEquals(Integer.valueOf(0), head.getReceivedFlag(), "没出过收讫");
    }

    @Test
    @DisplayName("空册再丢空串：照面，仍是空册")
    void emptyBook_secondEmpty_faceOnly() {
        CusIntakeReceipt first = intake(BIZ, SHIP_AGENT, null, new ArrayList<CusRowInput>());
        CusIntakeReceipt again = intake(BIZ, SHIP_AGENT, "10", new ArrayList<CusRowInput>());
        assertTrue(again.isFaceOnly());
        assertTrue(again.isEmptyBook());
        assertEquals(1, bookMapper.selectCount(new QueryWrapper<THeriCusBook>()
                .eq("biz_no", BIZ).eq("source", SHIP_AGENT)));
        assertEquals(0, cusBookService.listRows(first.getBookNo()).size());
    }

    // ---------------- 倒查 / 逐行点名 ----------------

    @Test
    @DisplayName("随便点一行倒查：落在哪档、计入哪笔，跟三笔对得上")
    void traceRow_locateAndTally() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "2", rows(
                line("E1", "10", "10", "20GP"),
                line("E2", "10", "8", "20GP")));

        CusRowTrace t1 = cusBookService.traceRow(rc.getBookNo(), 1);
        assertEquals("已签", t1.getStatusName());
        assertEquals("signed", t1.getTally());
        assertTrue(t1.isConsistent());
        assertNull(t1.getMismatchNote());

        CusRowTrace t2 = cusBookService.traceRow(rc.getBookNo(), 2);
        assertEquals("退回", t2.getStatusName());
        assertEquals("back", t2.getTally());
        assertEquals("少 2 件", t2.getBackReason());
        assertTrue(t2.isConsistent());

        assertTrue(cusBookService.audit(rc.getBookNo()).isEmpty(), "账齐时对账无话说");
    }

    @Test
    @DisplayName("三笔被改花了（页面抢先亮的数）：倒查与对账按行点名，以逐行为准")
    void audit_mismatch_namesRow() {
        CusIntakeReceipt rc = intake(BIZ, SHIP_AGENT, "2", rows(
                line("E1", "10", "10", "20GP"),
                line("E2", "10", "8", "20GP")));
        // 模拟头表汇总被人/页面抢先写成了不成立的数
        THeriCusBook head = cusBookService.selectByBookNo(rc.getBookNo());
        head.setInRows(9);
        head.setSignedRows(9);
        head.setBackRows(9);
        bookMapper.updateById(head);

        CusRowTrace t = cusBookService.traceRow(rc.getBookNo(), 2);
        assertFalse(t.isConsistent());
        assertTrue(t.getMismatchNote().contains("第2行"), t.getMismatchNote());
        assertTrue(t.getMismatchNote().contains("E2"));

        List<String> notes = cusBookService.audit(rc.getBookNo());
        assertFalse(notes.isEmpty());
        assertTrue(notes.get(0).contains("三笔对不上"));
        assertTrue(notes.stream().anyMatch(n -> n.contains("第1行") && n.contains("已签")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("第2行") && n.contains("退回")));
    }

    // ---------------- 同串并发 ----------------

    @Test
    @DisplayName("同一串 8 路并发进门：只立一册、行不重，后来的全走照面")
    void concurrentSameBook_onlyOneBook() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(8);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
        for (int i = 0; i < 8; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    cusBookService.intake(cmd(BIZ, SHIP_AGENT, "2", rows(
                            line("E1", "10", "10", "20GP"),
                            line("E2", "20", "20", "20GP"))), OP);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(errors.isEmpty(), "并发同串不应抛唯一约束异常: " + errors);
        assertEquals(1, bookMapper.selectCount(new QueryWrapper<THeriCusBook>()
                .eq("biz_no", BIZ).eq("source", SHIP_AGENT)));
        THeriCusBook only = cusBookService.selectByBiz(BIZ, SHIP_AGENT);
        assertEquals(2, cusBookService.listRows(only.getBookNo()).size());
        assertEquals(Integer.valueOf(2), only.getInRows());
        assertEquals(Integer.valueOf(2), only.getSignedRows());
        assertEquals(Integer.valueOf(0), only.getBackRows());
    }

    // ---------------- helpers ----------------

    private CusIntakeReceipt intake(String bizNo, String source, String declared, List<CusRowInput> rows) {
        return cusBookService.intake(cmd(bizNo, source, declared, rows), OP);
    }

    private static CusIntakeCmd cmd(String bizNo, String source, String declared, List<CusRowInput> rows) {
        CusIntakeCmd cmd = new CusIntakeCmd();
        cmd.setBizNo(bizNo);
        cmd.setSource(source);
        cmd.setDeclaredRows(declared);
        cmd.setShipName("远春河/2610E");
        cmd.setRows(rows);
        return cmd;
    }

    private static CusRowInput line(String entryId, String declareQty, String signedQty, String kind) {
        CusRowInput r = new CusRowInput();
        r.setEntryId(entryId);
        r.setDeclareQty(declareQty);
        r.setSignedQty(signedQty);
        r.setDeviceKind(kind);
        return r;
    }

    private static List<CusRowInput> rows(CusRowInput... rs) {
        List<CusRowInput> list = new ArrayList<CusRowInput>();
        for (CusRowInput r : rs) {
            list.add(r);
        }
        return list;
    }
}
