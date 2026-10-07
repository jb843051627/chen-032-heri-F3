package com.fc.v2.heri;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THeriDelBill;
import com.fc.v2.model.auto.THeriDelGate;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.DelAuditResult;
import com.fc.v2.model.custom.DelOpenCmd;
import com.fc.v2.model.custom.DelSignReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 港口放货台规矩集成测试（H2 + 真实 mapper）。
 * 蓝本就是 NB07 那票的实际流转：09:12 受理 → 09:40 单证齐全(乙) →
 * 09:41 同关后落笔(丙)压掉乙 → 10:05 结关封口 → 10:06 多一道不作数。
 *
 * @author fuce
 * @date 2026-10-07
 */
class THeriReleaseServiceTests extends ReleaseITSupport {

    private static final String NB07 = "NB07";
    private static final String JIA = "柜员甲";
    private static final String YI = "单证员乙";
    private static final String BING = "值班领导丙";
    private static final String DING = "闸口丁";

    @BeforeEach
    void init() throws Exception {
        setUp();
    }

    private static Date t(String hhmmss) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return f.parse("2026-10-07 " + hhmmss);
    }

    private THeriDelBill openNB07(String qty) {
        DelOpenCmd cmd = new DelOpenCmd();
        cmd.setBillNo(NB07);
        cmd.setQty(new BigDecimal(qty));
        cmd.setShipName("远东轮 07 航次");
        return releaseService.openBill(cmd, "册房");
    }

    private DelSignReceipt sign(String gate, String action, String who, String hhmmss) throws Exception {
        Integer g = gate == null ? null : Integer.valueOf(gate);
        return releaseService.sign(NB07, g, action, who, t(hhmmss));
    }

    private List<THeriDelGate> gates(int round) {
        return releaseService.listGates(NB07, round);
    }

    /** 一道只认一笔有效 */
    private THeriDelGate validGate(int round, int gate) {
        List<THeriDelGate> gs = gateMapper.selectList(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", NB07).eq("round_no", round).eq("gate_no", gate)
                .eq("status", THeriDelGate.ST_VALID));
        return gs.isEmpty() ? null : gs.get(0);
    }

    // ================= 分档看数量 =================

    @Test
    @DisplayName("分档看数量：直发档走3道、换单档2道、改提单档3道，立册誊死在头上")
    void grading_byQty_gatesCopiedToBill() {
        DelOpenCmd c1 = new DelOpenCmd();
        c1.setBillNo("NB10");
        c1.setQty(new BigDecimal("10"));
        THeriDelBill b1 = releaseService.openBill(c1, "册房");
        assertEquals("直发档", b1.getGradeName());
        assertEquals("直发", b1.getBizMode());
        assertEquals(Integer.valueOf(3), b1.getGateCount());
        assertEquals(Integer.valueOf(1), b1.getCurGate());

        DelOpenCmd c2 = new DelOpenCmd();
        c2.setBillNo("NB11");
        c2.setQty(new BigDecimal("100"));
        THeriDelBill b2 = releaseService.openBill(c2, "册房");
        assertEquals("换单档", b2.getGradeName());
        assertEquals(Integer.valueOf(2), b2.getGateCount());

        DelOpenCmd c3 = new DelOpenCmd();
        c3.setBillNo("NB12");
        c3.setQty(new BigDecimal("500"));
        THeriDelBill b3 = releaseService.openBill(c3, "册房");
        assertEquals("改提单档", b3.getGradeName());
        assertEquals(Integer.valueOf(3), b3.getGateCount());
    }

    @Test
    @DisplayName("判据没写到的数量：退回重定，系统不自己挑，册子不落")
    void grading_gap_returnedNotPicked() {
        DelOpenCmd cmd = new DelOpenCmd();
        cmd.setBillNo("NB99");
        cmd.setQty(new BigDecimal("1200"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> releaseService.openBill(cmd, "册房"));
        assertTrue(ex.getMessage().contains("退回重定"));
        assertNull(releaseService.selectByBillNo("NB99"));
    }

    @Test
    @DisplayName("数量刚好顶在上限：[min,max) 右端不含，50 件归换单档不归直发档")
    void grading_boundary_exclusiveMax() {
        DelOpenCmd cmd = new DelOpenCmd();
        cmd.setBillNo("NB13");
        cmd.setQty(new BigDecimal("50"));
        THeriDelBill b = releaseService.openBill(cmd, "册房");
        assertEquals("换单档", b.getGradeName());
    }

    @Test
    @DisplayName("同号再立只照面：数量/档一个字不改")
    void open_duplicateBillNo_faceOnly() {
        openNB07("10");
        DelOpenCmd again = new DelOpenCmd();
        again.setBillNo(NB07);
        again.setQty(new BigDecimal("800")); // 想换档，不认
        THeriDelBill b = releaseService.openBill(again, "册房");
        assertEquals("直发档", b.getGradeName());
        assertEquals(0, b.getQty().compareTo(new BigDecimal("10")));
    }

    // ================= NB07 蓝本全流转 =================

    @Test
    @DisplayName("NB07 全流转：受理→单证齐全被后落笔压掉→结关封口→多一道不作数")
    void nb07_fullTimeline() throws Exception {
        THeriDelBill bill = openNB07("10");
        assertEquals(Integer.valueOf(3), bill.getGateCount(), "直发档册上三道");

        // 09:12 关 1/3 受理
        DelSignReceipt r1 = sign(null, "受理", JIA, "09:12:00");
        assertTrue(r1.isCounted());
        assertEquals(Integer.valueOf(1), r1.getGateNo());
        assertFalse(r1.isSealed());
        assertEquals(Integer.valueOf(2), releaseService.selectByBillNo(NB07).getCurGate(),
                "落完第1道，下一道该签第2道");

        // 09:40 关 2/3 单证齐全（乙，悬着，等下一道）
        DelSignReceipt r2 = sign(null, "单证齐全", YI, "09:40:00");
        assertTrue(r2.isCounted());
        assertEquals(Integer.valueOf(2), r2.getGateNo());
        assertEquals(YI, validGate(1, 2).getSigner());

        // 09:41 关 2/3 单证齐全（丙，同关后落笔）：后落的算数，前笔作废留痕
        DelSignReceipt r3 = sign("2", "单证齐全", BING, "09:41:00");
        assertTrue(r3.isCounted(), "后落笔算数");
        assertEquals(BING, validGate(1, 2).getSigner());
        List<THeriDelGate> gate2 = gates(1).stream()
                .filter(g -> g.getGateNo() == 2).collect(java.util.stream.Collectors.toList());
        THeriDelGate yi = gate2.stream().filter(g -> YI.equals(g.getSigner())).findFirst().get();
        assertEquals(Integer.valueOf(THeriDelGate.ST_SUPERSEDED), yi.getStatus(),
                "09:40 乙那笔因 09:41 后落而作废");
        assertNotNull(yi.getVoidReason(), "作废笔痕必须留着缘由");
        assertNotNull(yi.getSupersededBy(), "记着被哪笔压掉");
        assertEquals(Integer.valueOf(1), yi.getTrailOk(), "作废那笔痕不许悄悄抹、也不是半成品");

        // 10:05 关 3/3 结关：末道落下即封口
        DelSignReceipt r4 = sign(null, "结关", DING, "10:05:00");
        assertTrue(r4.isCounted());
        assertTrue(r4.isSealed());
        THeriDelBill sealed = releaseService.selectByBillNo(NB07);
        assertEquals(Integer.valueOf(1), sealed.getStatus());
        assertEquals(Integer.valueOf(3), sealed.getCurGate());
        assertEquals(t("10:05:00"), sealed.getSealedTime());
        assertEquals(Integer.valueOf(1), sealed.getFinalRoundNo());

        // 10:06 关 4/3 结关（又一道）：册上三道、走了四道，多的不算数，以册上为准
        DelSignReceipt r5 = sign("4", "结关", DING, "10:06:00");
        assertFalse(r5.isCounted());
        assertEquals("AFTER_SEAL", r5.getOutcome());
        assertTrue(r5.getReason().contains("以册上为准"));
        THeriDelBill still = releaseService.selectByBillNo(NB07);
        assertEquals(Integer.valueOf(3), still.getCurGate(), "多一道不往册上排");
        assertEquals(t("10:05:00"), still.getSealedTime(), "封口时刻不被后笔改写");

        // 流水：5 道痕全在，作废的两笔谁都没抹
        List<THeriDelGate> all = gates(1);
        assertEquals(5, all.size());
        assertEquals(1, all.stream().filter(g -> g.getStatus() == THeriDelGate.ST_SUPERSEDED).count());
        assertEquals(1, all.stream().filter(g -> g.getStatus() == THeriDelGate.ST_AFTER_SEAL).count());

        // 留痕：谁/何时/退几回/最后哪道定的，都写了
        List<TSysOperLog> logs = trailLogs();
        assertTrue(logs.stream().anyMatch(l -> "受理".equals(l.getMethod()) && JIA.equals(l.getOperName())));
        assertTrue(logs.stream().anyMatch(l -> "压笔".equals(l.getMethod()) && BING.equals(l.getOperName())));
        assertTrue(logs.stream().anyMatch(l -> "封口".equals(l.getMethod())));
        assertTrue(logs.stream().anyMatch(l -> "作废".equals(l.getMethod()) && DING.equals(l.getOperName())));
    }

    // ================= 一道挨一道，不许跨 =================

    @Test
    @DisplayName("不许跨着走：头道空着直接签第2道，跨步作废，册上不推进")
    void sign_skipGate_voidAndNoAdvance() throws Exception {
        openNB07("10");
        DelSignReceipt rc = sign("2", "单证齐全", YI, "09:30:00");
        assertFalse(rc.isCounted());
        assertEquals("SKIP", rc.getOutcome());
        assertEquals(Integer.valueOf(1), releaseService.selectByBillNo(NB07).getCurGate());
        assertEquals(Integer.valueOf(0),
                releaseService.selectByBillNo(NB07).getRollbackCnt());
    }

    @Test
    @DisplayName("已定的那截不许回签：第1道定了再签第1道，数与次对不上、不作数")
    void sign_backToSettledGate_void() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        sign(null, "单证齐全", YI, "09:40:00");
        DelSignReceipt rc = sign("1", "受理", BING, "09:42:00");
        assertFalse(rc.isCounted());
        assertEquals("GATE_MISMATCH", rc.getOutcome());
        assertEquals(JIA, validGate(1, 1).getSigner(), "已定的还是原笔");
    }

    @Test
    @DisplayName("关次手填出格（关 9/3）：数与次对不上，以册上为准")
    void sign_gateBeyondBook_void() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        DelSignReceipt rc = sign("9", "??", BING, "09:13:00");
        assertFalse(rc.isCounted());
        assertEquals("GATE_MISMATCH", rc.getOutcome());
    }

    @Test
    @DisplayName("落签时刻早于在册同关那笔：时刻对不上，不认")
    void sign_earlierThanHanging_void() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        sign(null, "单证齐全", YI, "09:40:00");
        DelSignReceipt rc = sign("2", "单证齐全", BING, "09:30:00");
        assertFalse(rc.isCounted());
        assertEquals("GATE_MISMATCH", rc.getOutcome());
        assertEquals(YI, validGate(1, 2).getSigner());
    }

    @Test
    @DisplayName("关次系统排，不许手填：三道全靠 null 走下来，封口令出自第3道")
    void gates_systemOrdered_notHandFilled() throws Exception {
        openNB07("10");
        assertEquals(Integer.valueOf(1), sign(null, "受理", JIA, "09:12:00").getGateNo());
        assertEquals(Integer.valueOf(2), sign(null, "单证齐全", YI, "09:40:00").getGateNo());
        DelSignReceipt last = sign(null, "结关", DING, "10:05:00");
        assertEquals(Integer.valueOf(3), last.getGateNo());
        assertTrue(last.isSealed());
    }

    // ================= 真同时抢签：先落的算数 =================

    @Test
    @DisplayName("同一关同一刻两人抢签：并发错开，先落的算数，后到的作废记账（一道只留一笔有效）")
    void race_sameGateSameInstant_firstWins() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        final List<DelSignReceipt> results = new ArrayList<DelSignReceipt>();
        final AtomicInteger errors = new AtomicInteger();
        // 两个线程同一刻(09:40:00)抢签第 2 道
        String[] whos = {YI, BING};
        for (int i = 0; i < 2; i++) {
            final String who = whos[i];
            pool.submit(() -> {
                try {
                    ready.countDown();
                    assertTrue(go.await(5, TimeUnit.SECONDS));
                    DelSignReceipt rc = releaseService.sign(NB07, 2, "单证齐全", who, t("09:40:00"));
                    synchronized (results) {
                        results.add(rc);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(0, errors.get());

        long counted = results.stream().filter(DelSignReceipt::isCounted).count();
        long lost = results.stream().filter(r -> "LOST_RACE".equals(r.getOutcome())).count();
        assertEquals(1, counted, "先落的那一笔算数，就一笔");
        assertEquals(1, lost, "后到的那笔作废");
        // 一道只容一笔有效
        List<THeriDelGate> valid = gateMapper.selectList(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", NB07).eq("gate_no", 2).eq("status", THeriDelGate.ST_VALID));
        assertEquals(1, valid.size());
        // 册上没被抢封：第2道落下后游标指向第3道，还没封口
        THeriDelBill b = releaseService.selectByBillNo(NB07);
        assertEquals(Integer.valueOf(0), b.getStatus());
        assertEquals(Integer.valueOf(3), b.getCurGate());
    }

    @Test
    @DisplayName("末道同一刻抢签：先落的封口，后到的抢输作废，封口人是先落者")
    void race_lastGate_firstSeals() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        sign(null, "单证齐全", BING, "09:41:00");

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<DelSignReceipt> results = new ArrayList<DelSignReceipt>();
        String[] whos = {DING, "临时戊"};
        for (int i = 0; i < 2; i++) {
            final String who = whos[i];
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    DelSignReceipt rc = releaseService.sign(NB07, 3, "结关", who, t("10:05:00"));
                    synchronized (results) {
                        results.add(rc);
                    }
                } catch (Exception ignored) {
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(1, results.stream().filter(DelSignReceipt::isSealed).count());
        assertEquals(1, results.stream().filter(r -> "LOST_RACE".equals(r.getOutcome())).count());
        THeriDelBill b = releaseService.selectByBillNo(NB07);
        assertEquals(Integer.valueOf(1), b.getStatus());
        // 封口人是先落者（二者之一），且就是第3道唯一有效那笔
        String sealer = validGate(1, 3).getSigner();
        assertTrue(sealer.equals(DING) || sealer.equals("临时戊"));
        assertEquals(t("10:05:00"), b.getSealedTime());
    }

    // ================= 回退只摆悬着没定的段 =================

    @Test
    @DisplayName("回退只摆当前悬着没定的那道；已定的前几道原地不动、不跟着回来")
    void rollback_onlyPendingGate_settledStay() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");      // 第1道定
        sign(null, "单证齐全", YI, "09:40:00");   // 第2道悬着
        THeriDelBill after = releaseService.rollback(NB07, "值班领导丙");

        assertEquals(Integer.valueOf(2), after.getCurGate(), "仍停在第2道等重落，不退回第1道");
        assertEquals(Integer.valueOf(1), after.getRollbackCnt(), "退了一笔记一回");
        assertNull(validGate(1, 2), "第2道悬笔被摆回，没有有效笔了");
        assertNotNull(validGate(1, 1), "第1道已定，原地不动");
        THeriDelGate hung = gateMapper.selectList(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", NB07).eq("gate_no", 2)
                .eq("status", THeriDelGate.ST_ROLLED_BACK)).get(0);
        assertNotNull(hung.getVoidReason(), "摆回的笔痕留着");

        // 重落第2道：同一道重新算数，接着往下走完
        assertTrue(sign("2", "单证齐全", BING, "11:00:00").isCounted());
        DelSignReceipt seal = sign(null, "结关", DING, "11:05:00");
        assertTrue(seal.isSealed());

        // 已定的那截不许因为退过一回就当没发生：第1道还是甲 09:12 那笔
        assertEquals(JIA, validGate(1, 1).getSigner());
        assertEquals(t("09:12:00"), validGate(1, 1).getLandTime());
        // 留痕里写得清退过几回
        assertTrue(trailLogs().stream()
                .anyMatch(l -> "回退".equals(l.getMethod()) && l.getOperParam().contains("累计退 1 回")));
    }

    @Test
    @DisplayName("封口后不许回退；要改先整件收口重来")
    void rollback_sealed_rejected() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        sign(null, "单证齐全", BING, "09:41:00");
        sign(null, "结关", DING, "10:05:00");
        assertThrows(IllegalStateException.class, () -> releaseService.rollback(NB07, BING));
    }

    @Test
    @DisplayName("收口重来：旧遍老账一条不动，另起一遍从第1道走，以新遍封口令为准")
    void reopen_sealed_oldRoundUntouched() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        sign(null, "单证齐全", BING, "09:41:00");
        sign(null, "结关", DING, "10:05:00");

        THeriDelBill reopened = releaseService.reopen(NB07, BING);
        assertEquals(Integer.valueOf(2), reopened.getRoundNo());
        assertEquals(Integer.valueOf(0), reopened.getStatus());
        assertEquals(Integer.valueOf(1), reopened.getCurGate());
        assertEquals(Integer.valueOf(3), reopened.getGateCount(), "仍走册上三道");
        assertNull(reopened.getSealedTime());

        // 老遍（第1遍）一条不动：干净走齐的三道，照旧查得回
        assertEquals(DING, validGate(1, 3).getSigner());
        assertEquals(3, gates(1).size(), "第1遍就三笔，道道有效");

        // 新遍从头走，跟第1遍互不干扰
        sign(null, "受理", "新柜员己", "13:00:00");
        sign(null, "单证齐全", "新单证庚", "13:10:00");
        DelSignReceipt seal2 = sign(null, "结关", "新闸口辛", "13:20:00");
        assertTrue(seal2.isSealed());
        THeriDelBill now = releaseService.selectByBillNo(NB07);
        assertEquals(Integer.valueOf(2), now.getFinalRoundNo(), "以新遍为准");
        assertEquals(Integer.valueOf(1), now.getStatus());
        assertEquals(t("13:20:00"), now.getSealedTime());
        // 老遍的痕一点没动
        assertEquals(DING, validGate(1, 3).getSigner());
        assertEquals("新闸口辛", validGate(2, 3).getSigner());
    }

    // ================= 老单子：加关口不追老账 =================

    @Test
    @DisplayName("往年已走齐的老单子：判据后来加关口，老册子照旧三道、照旧查得回、一条不动")
    void oldBills_gradeAddsGates_untouched() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        sign(null, "单证齐全", BING, "09:41:00");
        DelSignReceipt seal = sign(null, "结关", DING, "10:05:00");
        assertTrue(seal.isSealed());
        int oldGateRows = gates(1).size();

        // 后来港里把直发档加成 5 道关
        com.fc.v2.model.auto.THeriDelGrade g =
                gradeMapper.selectOne(new QueryWrapper<com.fc.v2.model.auto.THeriDelGrade>()
                        .eq("grade_code", "DG01"));
        g.setGates(5);
        gradeMapper.updateById(g);

        THeriDelBill old = releaseService.selectByBillNo(NB07);
        assertEquals(Integer.valueOf(3), old.getGateCount(), "老册子头上誊死三道，不追判据");
        assertEquals(Integer.valueOf(1), old.getStatus(), "老封口照旧封着");
        assertEquals(oldGateRows, gates(1).size(), "老账一条不动");
        // 次日还想加签：封口令之后，照样顶回，且按三道说“多出来”
        DelSignReceipt extraNext = releaseService.sign(NB07, 4, "结关", DING,
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-10-08 09:00:00"));
        assertFalse(extraNext.isCounted());
        assertEquals("AFTER_SEAL", extraNext.getOutcome());
        assertTrue(extraNext.getReason().contains("多出来"));

        // 新开的册子才按新判据走 5 道
        DelOpenCmd fresh = new DelOpenCmd();
        fresh.setBillNo("NB20");
        fresh.setQty(new BigDecimal("10"));
        assertEquals(Integer.valueOf(5), releaseService.openBill(fresh, "册房").getGateCount());
    }

    // ================= 签认和留痕同一次完成；重办只补没写的 =================

    @Test
    @DisplayName("签认与留痕同一次完成：每笔有效签都有对应留痕，只落一处的半成品倒核不算数")
    void sign_andTrailLandTogether() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        THeriDelGate g1 = validGate(1, 1);
        assertEquals(Integer.valueOf(1), g1.getTrailOk());
        assertTrue(trailCount(JIA) >= 1, "签认当场有留痕");
    }

    @Test
    @DisplayName("中途出错只落了签认：重办只补没写的留痕，原笔不重落、时刻/人不改")
    void retry_onlyCompletesMissingTrail() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");

        // 下一笔落签时让留痕失败一次：签认已落（trail_ok=0 半成品）
        com.fc.v2.service.impl.THeriReleaseServiceImpl broken =
                new com.fc.v2.service.impl.THeriReleaseServiceImpl() {
                    boolean first = true;
                    @Override
                    protected void writeTrail(String billNo, int round, String action,
                                              String signer, String param) {
                        if (first && "单证齐全".equals(action)) {
                            first = false;
                            throw new RuntimeException("留痕库抖了一下");
                        }
                        super.writeTrail(billNo, round, action, signer, param);
                    }
                };
        org.springframework.test.util.ReflectionTestUtils.setField(broken,
                "delGradeMapper", gradeMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(broken,
                "delBillMapper", billMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(broken,
                "delGateMapper", gateMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(broken,
                "sysOperLogMapper", operLogMapper);

        assertThrows(RuntimeException.class,
                () -> broken.sign(NB07, null, "单证齐全", YI, t("09:40:00")));
        // 签认在、留痕缺：半成品挂着
        THeriDelGate half = gateMapper.selectList(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", NB07).eq("gate_no", 2).eq("trail_ok", 0)).get(0);
        assertEquals(YI, half.getSigner());
        assertEquals(t("09:40:00"), half.getLandTime());

        // 重办：原笔不重落，只补没写的留痕；册上推进到第2道，落笔时刻还是 09:40
        DelSignReceipt rc = broken.sign(NB07, null, "单证齐全", YI, t("09:40:00"));
        assertTrue(rc.isCounted());
        THeriDelGate now = validGate(1, 2);
        assertEquals(half.getId(), now.getId(), "还是原那笔，没另落");
        assertEquals(Integer.valueOf(1), now.getTrailOk());
        assertEquals(t("09:40:00"), now.getLandTime());
        assertEquals(Integer.valueOf(3), releaseService.selectByBillNo(NB07).getCurGate(),
                "补完后照常推进到下一道");
    }

    // ================= 抽验：随机抽一单倒着核 =================

    @Test
    @DisplayName("抽验倒核：NB07 从末道往头道核，三道都核得出；作废笔痕核得出是作废，整遍算数")
    void audit_backwardFromLastGate_passWithVoidTraces() throws Exception {
        nb07_fullTimeline();
        DelAuditResult a = releaseService.auditBackward(NB07);
        assertTrue(a.isPass());
        assertEquals(java.util.Arrays.asList(3, 2, 1), a.getVerifiedGates(), "末道在前倒着核");
        assertEquals(Integer.valueOf(1), a.getRoundNo());
        assertTrue(a.isSealed());
        // 乙被压 + 丁多一道，两笔作废痕都核得出、点得明，但不连累正链
        assertEquals(2, a.getVoidedTraces().size());
        assertTrue(a.getProblems().isEmpty());
    }

    @Test
    @DisplayName("抽验倒核：还在办、末道空着，核得出几道算几道，缺的那几笔不作数")
    void audit_running_missingLastGate_notCounted() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        sign(null, "单证齐全", YI, "09:40:00");
        DelAuditResult a = releaseService.auditBackward(NB07);
        assertFalse(a.isPass());
        assertFalse(a.isSealed());
        assertEquals(java.util.Arrays.asList(2, 1), a.getVerifiedGates());
        assertTrue(a.getProblems().stream().anyMatch(p -> p.contains("第 3 道") && p.contains("空着")));
    }

    @Test
    @DisplayName("抽验倒核：半成品（只落签认没留痕）核不出，那笔不作数")
    void audit_halfRow_onlyOneLedger_notCounted() throws Exception {
        openNB07("10");
        sign(null, "受理", JIA, "09:12:00");
        // 直接造一笔只落一处的半成品
        THeriDelGate half = new THeriDelGate();
        half.setBillNo(NB07);
        half.setRoundNo(1);
        half.setGateNo(2);
        half.setGateTotal(3);
        half.setSigner(YI);
        half.setSignAction("单证齐全");
        half.setLandTime(t("09:40:00"));
        half.setLandSeq(99L);
        half.setStatus(THeriDelGate.ST_VALID);
        half.setTrailOk(0);
        half.setDelFlag(0);
        half.setCreateBy(YI);
        half.setCreateTime(new Date());
        gateMapper.insert(half);

        DelAuditResult a = releaseService.auditBackward(NB07);
        assertFalse(a.isPass());
        assertFalse(a.getVerifiedGates().contains(2), "第2道只落一处，核不出");
        assertTrue(a.getProblems().stream().anyMatch(p -> p.contains("只落了一处")));
    }

    private List<TSysOperLog> trailLogs() {
        return operLogMapper.selectList(new QueryWrapper<TSysOperLog>()
                .eq("oper_url", "/heri/release/" + NB07).orderByAsc("oper_time"));
    }

    private int trailCount(String who) {
        return operLogMapper.selectCount(new QueryWrapper<TSysOperLog>()
                .eq("oper_url", "/heri/release/" + NB07).eq("create_by", who));
    }
}
