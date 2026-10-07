package com.fc.v2.heri;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriDelRow;
import com.fc.v2.model.auto.THeriDelRule;
import com.fc.v2.model.auto.THeriDelSign;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.DelAudit;
import com.fc.v2.model.custom.DelStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 港口放货台规矩集成测试（H2 + 真实 mapper + 真事务）。
 *
 * <p>头一个用例把册上那趟 NB07 逐行回放；其余用例把口径一条条坐实。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
class THeriDelRuleServiceTests extends DelRuleITSupport {

    private static final String BILL = "NB07";

    // 签认册：这笔算不算数 0有效 1作废 2被拒
    private static final int STROKE_OK = 0;
    private static final int STROKE_VOID = 1;
    private static final int STROKE_REJ = 2;
    // 凭证头：0在办 1已核发 2已封住
    private static final int HEAD_RUNNING = 0;
    private static final int HEAD_ISSUED = 1;
    private static final int HEAD_CLOSED = 2;

    @BeforeEach
    void init() throws Exception {
        setUp();
    }

    // ---------------- 册上那趟 NB07：逐行回放 ----------------

    @Test
    @DisplayName("NB07 回放：09:40 被 09:41 压下作废留痕，10:05 关齐封口，10:06 多的一道不算数")
    void nb07Record_replay() {
        THeriDelRule opened = delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        assertEquals("直发档", opened.getGrade(), "分档看数量：300 件归直发档");
        assertEquals(Integer.valueOf(3), opened.getGateTotal(), "直发档走三道，册上写死");

        DelStroke s1 = delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "NB07-1", null);
        DelStroke s2 = delRuleService.sign(BILL, "单证员乙", "单证齐全", at("09:40"), "NB07-2", null);
        DelStroke s3 = delRuleService.resign(BILL, "值班领导丙", "单证齐全", at("09:41"), "NB07-3", null);
        DelStroke s4 = delRuleService.sign(BILL, "闸口丁", "结关", at("10:05"), "NB07-4", null);
        DelStroke s5 = delRuleService.sign(BILL, "闸口丁", "结关", at("10:06"), "NB07-5", null);

        // 09:12 关1/3 受理 柜员甲：落成
        assertEquals(DelStroke.OUT_SIGN, s1.getOutcome());
        assertEquals(Integer.valueOf(1), s1.getStroke().getGateNo());
        assertEquals("柜员甲", s1.getStroke().getSigner());
        assertEquals("受理", s1.getStroke().getAction());

        // 09:40 关2/3 单证齐全 单证员乙：当时落成，后被 09:41 压下作废——痕必须留着
        assertEquals(DelStroke.OUT_SIGN, s2.getOutcome());
        THeriDelSign s2Now = stroke(s2.getStroke().getId());
        assertEquals(STROKE_VOID, s2Now.getStatus(), "09:40 那笔被后一笔压下作废");
        assertEquals("被后一笔压下", s2Now.getVoidReason());
        assertNull(s2Now.getEffGate(), "作废笔不占关次位");

        // 09:41 关2/3 单证齐全 值班领导丙：后落的那一道算数，并指回被压的那笔
        assertEquals(DelStroke.OUT_RESIGN, s3.getOutcome());
        assertEquals(Integer.valueOf(2), s3.getStroke().getGateNo());
        assertEquals(s2.getStroke().getId(), s3.getStroke().getSupersedes(), "压了哪一笔指回 09:40");

        // 10:05 关3/3 结关 闸口丁：关齐即封口
        assertEquals(DelStroke.OUT_SIGN, s4.getOutcome());
        assertEquals(Integer.valueOf(3), s4.getStroke().getGateNo());

        // 10:06 关4/3：册上三道、系统走了四道——这趟不算数，以册上为准，痕留着
        assertEquals(DelStroke.OUT_REJECTED, s5.getOutcome());
        assertFalse(s5.counts());
        assertEquals(Integer.valueOf(4), s5.getStroke().getGateNo());
        assertEquals(STROKE_REJ, s5.getStroke().getStatus());
        assertTrue(s5.getStroke().getRejectReason().contains("多了一道"), s5.getStroke().getRejectReason());
        assertTrue(s5.getStroke().getRejectReason().contains("以册上为准"), s5.getStroke().getRejectReason());

        // 头表：关数 3/3，已核发，退了几回=0
        THeriDelRule head = head(BILL);
        assertEquals(Integer.valueOf(3), head.getNodeNo());
        assertEquals(HEAD_ISSUED, head.getStatus());
        assertEquals(Integer.valueOf(0), head.getRollbackCnt());

        // 签认册：五笔都在——3 有效、1 作废、1 被拒，一道只容一笔有效
        List<THeriDelSign> strokes = strokes(BILL, 1);
        assertEquals(5, strokes.size());
        assertEquals(3, countByStatus(strokes, STROKE_OK));
        assertEquals(1, countByStatus(strokes, STROKE_VOID));
        assertEquals(1, countByStatus(strokes, STROKE_REJ));
        assertEquals(1, effectiveAtGate(strokes, 2), "关2 只容一笔有效（值班领导丙）");
        assertEquals("值班领导丙", effectiveStrokeAtGate(strokes, 2).getSigner());

        // 留痕册：开单、落签×3、压签、作废、封口、被拒，一笔不缺
        assertEquals(1, countTrail(BILL, "开单"));
        assertEquals(3, countTrail(BILL, "落签"));
        assertEquals(1, countTrail(BILL, "压签"));
        assertEquals(1, countTrail(BILL, "作废"));
        assertEquals(1, countTrail(BILL, "封口"));
        assertEquals(1, countTrail(BILL, "被拒"));
        TSysOperLog seal = trailOf(BILL, "封口");
        assertTrue(seal.getOperParam().contains("最后哪一道定的=第3道"), seal.getOperParam());
        assertTrue(seal.getOperParam().contains("闸口丁"), seal.getOperParam());

        // 抽验倒着核：全算数
        DelAudit audit = delRuleService.audit(BILL, 1);
        assertTrue(audit.isOk(), String.join("；", audit.getNotes()));
        assertEquals(5, audit.getReconciled().size());
        assertTrue(audit.getUnreconciled().isEmpty());
    }

    // ---------------- 两人同时抢着签：并发错开，先落的算数 ----------------

    @Test
    @DisplayName("8 路同时抢签同一道：先落的那一笔算数，后到的作废并记账，一道只容一笔")
    void raceSign_firstLandedWins() throws Exception {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(8);
        List<DelStroke> results = Collections.synchronizedList(new ArrayList<DelStroke>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
        for (int i = 0; i < 8; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    // 都看到册上一笔没有（baseStroke=0），同时落笔抢第 1 道
                    results.add(delRuleService.sign(BILL, "柜员" + n, "受理", at("09:12"),
                            "RACE-" + n, 0L));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(errors.isEmpty(), "并发抢签不该抛异常: " + errors);
        int signed = 0;
        int voided = 0;
        for (DelStroke r : results) {
            if (DelStroke.OUT_SIGN.equals(r.getOutcome())) {
                signed++;
            } else if (DelStroke.OUT_VOIDED.equals(r.getOutcome())) {
                voided++;
                assertEquals("抢签落败", r.getStroke().getVoidReason());
            }
        }
        assertEquals(1, signed, "先落的那一笔算数，只许落成一笔");
        assertEquals(7, voided, "后到的全作废并记账");

        List<THeriDelSign> strokes = strokes(BILL, 1);
        assertEquals(8, strokes.size(), "作废的痕也都在册上，不许悄悄抹掉");
        assertEquals(1, countByStatus(strokes, STROKE_OK));
        assertEquals(7, countByStatus(strokes, STROKE_VOID));
        assertEquals(Integer.valueOf(1), head(BILL).getNodeNo());
        assertTrue(delRuleService.audit(BILL, 1).isOk());
    }

    // ---------------- 后一笔压前一笔：顺序重签 ----------------

    @Test
    @DisplayName("顺序重签：后一笔压前一笔，前一笔作废留痕、新笔指回去；已定的不许动")
    void sequentialResign_supersedesKeepsTrace() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        DelStroke s1 = delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "S-1", null);
        DelStroke re = delRuleService.resign(BILL, "值班领导丙", "受理", at("09:20"), "S-2", null);

        assertEquals(DelStroke.OUT_RESIGN, re.getOutcome());
        assertEquals(s1.getStroke().getId(), re.getStroke().getSupersedes());
        assertEquals(STROKE_VOID, stroke(s1.getStroke().getId()).getStatus(), "前一笔作废，痕留着");
        assertEquals(STROKE_OK, stroke(re.getStroke().getId()).getStatus());
        assertEquals(Integer.valueOf(1), head(BILL).getNodeNo(), "压签不推进关数，还是第 1 道落定");
        assertEquals(1, countTrail(BILL, "压签"));
        assertEquals(1, countTrail(BILL, "作废"));
        assertTrue(delRuleService.audit(BILL, 1).isOk());
    }

    @Test
    @DisplayName("压签也讲先落：看到的那笔跟册上对不上，后到的那一笔作废并记账")
    void resign_raceLoser() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        DelStroke s1 = delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "S-1", null);
        // 丙看着 s1 要压，落笔时册上悬着的已不是 s1（先被别人压过一回）——后到的作废
        DelStroke first = delRuleService.resign(BILL, "值班领导丙", "受理", at("09:20"), "S-2", null);
        DelStroke late = delRuleService.resign(BILL, "单证员乙", "受理", at("09:21"), "S-3",
                s1.getStroke().getId());

        assertEquals(DelStroke.OUT_RESIGN, first.getOutcome());
        assertEquals(DelStroke.OUT_VOIDED, late.getOutcome());
        assertEquals("抢签落败", late.getStroke().getVoidReason());
        assertEquals(STROKE_OK, stroke(first.getStroke().getId()).getStatus(), "先落的算数");
        assertEquals(1, effectiveAtGate(strokes(BILL, 1), 1));
        assertTrue(delRuleService.audit(BILL, 1).isOk());
    }

    // ---------------- 回退：只退悬着的那道，已定的原地不动 ----------------

    @Test
    @DisplayName("回退：只退悬着的那道，已定的原地不动不当没发生过；退了几回记几回")
    void rollback_onlyHanging_lockedGatesStay() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        DelStroke g1 = delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "S-1", null);
        DelStroke g2 = delRuleService.sign(BILL, "单证员乙", "单证齐全", at("09:40"), "S-2", null);

        DelStroke back = delRuleService.rollback(BILL, "值班领导丙", at("09:50"), "单证要重核");
        assertEquals(DelStroke.OUT_ROLLBACK, back.getOutcome());
        assertEquals(g2.getStroke().getId(), back.getStroke().getId(), "退的是悬着的第 2 道");
        assertEquals(STROKE_VOID, stroke(g2.getStroke().getId()).getStatus());
        assertEquals("回退冲下", stroke(g2.getStroke().getId()).getVoidReason());

        // 已定的第 1 道：原地不动，不许因为后来退了一回就当没发生过
        THeriDelSign g1Now = stroke(g1.getStroke().getId());
        assertEquals(STROKE_OK, g1Now.getStatus(), "第 1 道已定，原地不动");
        assertEquals(Integer.valueOf(1), g1Now.getEffGate());
        assertEquals(Integer.valueOf(1), head(BILL).getNodeNo());
        assertEquals(Integer.valueOf(1), head(BILL).getRollbackCnt(), "退了几回记一回");
        assertTrue(trailOf(BILL, "回退").getOperParam().contains("第1回退"));

        // 退完接着走：重签第 2 道，关次还是系统派
        DelStroke g2b = delRuleService.sign(BILL, "单证员乙", "单证齐全", at("10:00"), "S-3", null);
        assertEquals(Integer.valueOf(2), g2b.getStroke().getGateNo());
        assertTrue(delRuleService.audit(BILL, 1).isOk());

        // 一路退到底：第 2 道、第 1 道依次退下（每回只退悬着的那道），退了几回=3
        delRuleService.rollback(BILL, "值班领导丙", at("10:10"), null);
        delRuleService.rollback(BILL, "值班领导丙", at("10:11"), null);
        assertEquals(Integer.valueOf(0), head(BILL).getNodeNo());
        assertEquals(Integer.valueOf(3), head(BILL).getRollbackCnt());
        assertEquals(3, countTrail(BILL, "回退"));
        assertTrue(delRuleService.audit(BILL, 1).isOk());

        // 一道都没了还退：没的可退
        assertThrows(IllegalStateException.class,
                () -> delRuleService.rollback(BILL, "值班领导丙", at("10:12"), null));
    }

    // ---------------- 关齐即封 ----------------

    @Test
    @DisplayName("关齐即封：再签、再压、再退全被拒留痕；要加签先把整件事收口重来")
    void sealed_noMoreStrokes() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "S-1", null);
        delRuleService.sign(BILL, "单证员乙", "单证齐全", at("09:40"), "S-2", null);
        delRuleService.sign(BILL, "闸口丁", "结关", at("10:05"), "S-3", null);
        assertEquals(HEAD_ISSUED, head(BILL).getStatus(), "三道齐即封口");

        DelStroke extra = delRuleService.sign(BILL, "闸口丁", "结关", at("10:06"), "S-4", null);
        assertEquals(DelStroke.OUT_REJECTED, extra.getOutcome());
        assertTrue(extra.getStroke().getRejectReason().contains("多了一道"));

        DelStroke re = delRuleService.resign(BILL, "值班领导丙", "结关", at("10:07"), "S-5", null);
        assertEquals(DelStroke.OUT_REJECTED, re.getOutcome());
        assertTrue(re.getStroke().getRejectReason().contains("已封口"));

        DelStroke back = delRuleService.rollback(BILL, "值班领导丙", at("10:08"), null);
        assertEquals(DelStroke.OUT_REJECTED, back.getOutcome());
        assertTrue(back.getStroke().getRejectReason().contains("已封口"));

        THeriDelRule head = head(BILL);
        assertEquals(Integer.valueOf(3), head.getNodeNo(), "被拒的三笔一道没动");
        assertEquals(3, countByStatus(strokes(BILL, 1), STROKE_REJ));
        assertTrue(delRuleService.audit(BILL, 1).isOk());
    }

    // ---------------- 收口重来：老趟一条不动 ----------------

    @Test
    @DisplayName("收口重来：老趟封住照旧查得回，新趟照册上写死的档和关数重走")
    void redo_oldRoundUntouched() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "S-1", null);
        delRuleService.sign(BILL, "单证员乙", "单证齐全", at("09:40"), "S-2", null);
        delRuleService.sign(BILL, "闸口丁", "结关", at("10:05"), "S-3", null);

        THeriDelRule round2 = delRuleService.redo(BILL, "值班领导丙", at("11:00"));
        assertEquals(Integer.valueOf(2), round2.getRoundNo());
        assertEquals(HEAD_RUNNING, round2.getStatus());
        assertEquals("直发档", round2.getGrade(), "档照册上写死的来，不替人重定");
        assertEquals(Integer.valueOf(3), round2.getGateTotal());

        // 老趟封住，一条不动，照旧查得回
        THeriDelRule round1 = delRuleService.selectRound(BILL, 1);
        assertEquals(HEAD_CLOSED, round1.getStatus());
        assertEquals(Integer.valueOf(3), round1.getNodeNo());
        assertEquals(3, strokes(BILL, 1).size());
        assertTrue(delRuleService.audit(BILL, 1).isOk(), "老趟照样核得出");

        // 新趟从头走，关次重新排
        DelStroke again = delRuleService.sign(BILL, "柜员甲", "受理", at("11:10"), "R2-1", null);
        assertEquals(Integer.valueOf(1), again.getStroke().getGateNo());
        assertEquals(Integer.valueOf(2), again.getStroke().getRoundNo());
        assertEquals(1, strokes(BILL, 2).size());
        assertEquals(3, strokes(BILL, 1).size(), "新趟落笔不碰老趟");
        assertTrue(delRuleService.audit(BILL, 2).isOk());

        // 没封口就谈不上收口重来
        assertThrows(IllegalStateException.class,
                () -> delRuleService.redo(BILL, "值班领导丙", at("11:20")));
    }

    // ---------------- 中途出错停下，重办只补没写的那几步 ----------------

    @Test
    @DisplayName("同一笔重办：照面不重复落；补齐接着走，关次不重排")
    void idempotentRetry_fillsOnlyMissing() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        DelStroke first = delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "ATT-1", null);
        // 同一笔重办（比如提交后没等到回话又点了一遍）
        DelStroke again = delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "ATT-1", null);

        assertEquals(DelStroke.OUT_FACE_ONLY, again.getOutcome());
        assertEquals(first.getStroke().getId(), again.getStroke().getId());
        assertEquals(1, strokes(BILL, 1).size(), "同一笔不重复落");
        assertEquals(1, countTrail(BILL, "落签"), "痕也只有一笔");

        // 没写的那几步接着补：下一道照排第 2 道
        DelStroke next = delRuleService.sign(BILL, "单证员乙", "单证齐全", at("09:40"), "ATT-2", null);
        assertEquals(Integer.valueOf(2), next.getStroke().getGateNo());
        assertEquals(2, strokes(BILL, 1).size());
    }

    // ---------------- 两边账本：签认和留痕同一事务落 ----------------

    @Test
    @DisplayName("留痕册写失败：签认册也不许落（不能只落一处），头表一道没动")
    void atomicDualLedger_trailFailsNoStroke() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");

        // 留痕册在"炸点"这笔上写库失败
        TSysOperLogMapper real = this.operLogMapper;
        TSysOperLogMapper failing = (TSysOperLogMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {TSysOperLogMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName()) && args != null
                            && args.length == 1 && args[0] instanceof TSysOperLog
                            && String.valueOf(((TSysOperLog) args[0]).getOperParam()).contains("炸点")) {
                        throw new RuntimeException("留痕册写库失败");
                    }
                    return method.invoke(real, args);
                });
        ReflectionTestUtils.setField(delRuleService, "sysOperLogMapper", failing);

        assertThrows(RuntimeException.class,
                () -> delRuleService.sign(BILL, "柜员甲", "炸点受理", at("09:12"), "BOOM-1", null));
        assertEquals(0, strokes(BILL, 1).size(), "留痕没落成，签认也不许落");
        assertEquals(Integer.valueOf(0), head(BILL).getNodeNo(), "头表一道没动");
        assertEquals(0, countTrail(BILL, "落签"));

        // 换回好使的留痕册：同一笔重办照落（中途出错停下，重办只补没写的那几步）
        ReflectionTestUtils.setField(delRuleService, "sysOperLogMapper", real);
        DelStroke retry = delRuleService.sign(BILL, "柜员甲", "受理", at("09:13"), "BOOM-1", null);
        assertEquals(DelStroke.OUT_SIGN, retry.getOutcome());
        assertEquals(1, strokes(BILL, 1).size());
        assertTrue(delRuleService.audit(BILL, 1).isOk());
    }

    // ---------------- 分档：看数量，港里判据，判据没写到退回重定 ----------------

    @Test
    @DisplayName("分档看数量：500 内直发、2000 内换单、5000 内改提单；超界退回重定不自己挑")
    void classify_byQty_retierWhenUncovered() {
        delRuleService.setGradeGates(java.util.Collections.singletonMap("换单档", 2));
        delRuleService.setGradeGates(java.util.Collections.singletonMap("改提单档", 4));

        assertEquals("直发档", delRuleService.open("NB-A1", new BigDecimal("300"), at("09:00"), "开单员").getGrade());
        assertEquals("直发档", delRuleService.open("NB-A2", new BigDecimal("500"), at("09:00"), "开单员").getGrade(),
                "500 件以内含 500");
        assertEquals("换单档", delRuleService.open("NB-A3", new BigDecimal("1500"), at("09:00"), "开单员").getGrade());
        assertEquals("改提单档", delRuleService.open("NB-A4", new BigDecimal("3000"), at("09:00"), "开单员").getGrade());

        // 判据没写到的（6000 件超界）：退回重定，系统不自己挑；单没立，痕留着
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> delRuleService.open("NB-A5", new BigDecimal("6000"), at("09:00"), "开单员"));
        assertTrue(e.getMessage().contains("退回重定"), e.getMessage());
        assertNull(head("NB-A5"), "退回重定不落单");
        assertEquals(1, countTrail("NB-A5", "退回重定"), "被退也留一笔痕");
    }

    @Test
    @DisplayName("档定得下、几道关判据没写到：照样退回重定，系统不自己挑")
    void gradeWithoutGateCount_retier() {
        // 港里只颁了直发档=3，换单档走几道判据没写到
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> delRuleService.open("NB-G1", new BigDecimal("1500"), at("09:00"), "开单员"));
        assertTrue(e.getMessage().contains("退回重定"), e.getMessage());
        assertNull(head("NB-G1"), "判据没写到不落单");
        assertEquals(1, countTrail("NB-G1", "退回重定"));
    }

    @Test
    @DisplayName("说两道走两道：换单档走两道即封口，第三道是被拒的多余道")
    void twoGatesTier_walksTwo() {
        delRuleService.setGradeGates(java.util.Collections.singletonMap("换单档", 2));
        delRuleService.open(BILL, new BigDecimal("1500"), at("09:00"), "开单员");
        assertEquals(Integer.valueOf(2), head(BILL).getGateTotal());

        delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "S-1", null);
        delRuleService.sign(BILL, "闸口丁", "结关", at("09:40"), "S-2", null);
        assertEquals(HEAD_ISSUED, head(BILL).getStatus(), "两道齐即封口");

        DelStroke extra = delRuleService.sign(BILL, "闸口丁", "结关", at("09:41"), "S-3", null);
        assertEquals(DelStroke.OUT_REJECTED, extra.getOutcome());
        assertTrue(delRuleService.audit(BILL, 1).isOk());
    }

    @Test
    @DisplayName("往后加关口不许追着改老账：老单照开单时写死的三道走，新单才走四道")
    void addGateLater_oldBillsUntouched() {
        delRuleService.open("NB-OLD", new BigDecimal("300"), at("09:00"), "开单员");
        // 港里改判据：直发档往后走四道
        delRuleService.setGradeGates(java.util.Collections.singletonMap("直发档", 4));
        delRuleService.open("NB-NEW", new BigDecimal("300"), at("09:00"), "开单员");

        assertEquals(Integer.valueOf(3), head("NB-OLD").getGateTotal(), "老单写死的三道不追改");
        assertEquals(Integer.valueOf(4), head("NB-NEW").getGateTotal(), "新单按新判据走四道");

        // 老单三道即封；新单三道还没齐，四道才封
        for (int i = 1; i <= 3; i++) {
            delRuleService.sign("NB-OLD", "柜员甲", "受理", at("09:1" + i), "O-" + i, null);
            delRuleService.sign("NB-NEW", "柜员甲", "受理", at("09:2" + i), "N-" + i, null);
        }
        assertEquals(HEAD_ISSUED, head("NB-OLD").getStatus());
        assertEquals(HEAD_RUNNING, head("NB-NEW").getStatus(), "新单四道才齐");
        delRuleService.sign("NB-NEW", "闸口丁", "结关", at("09:30"), "N-4", null);
        assertEquals(HEAD_ISSUED, head("NB-NEW").getStatus());
        assertTrue(delRuleService.audit("NB-OLD", 1).isOk());
        assertTrue(delRuleService.audit("NB-NEW", 1).isOk());
    }

    // ---------------- 抽验：核得出的算数，核不出的不作数；页面先亮的不算数 ----------------

    @Test
    @DisplayName("落签痕被人抹了一笔：抽验倒着核点出那笔不作数")
    void audit_tamperedTrail_flagged() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "S-1", null);
        DelStroke s2 = delRuleService.sign(BILL, "单证员乙", "单证齐全", at("09:40"), "S-2", null);
        delRuleService.sign(BILL, "闸口丁", "结关", at("10:05"), "S-3", null);
        assertTrue(delRuleService.audit(BILL, 1).isOk(), "账齐时抽验无话说");

        // 悄悄抹掉第 2 道的落签痕
        TSysOperLog trail = null;
        for (TSysOperLog t : delRuleService.listTrail(BILL)) {
            if ("落签".equals(t.getMethod()) && t.getOperParam().contains("关=2")) {
                trail = t;
            }
        }
        assertNotNull(trail);
        operLogMapper.deleteById(trail.getId());

        DelAudit audit = delRuleService.audit(BILL, 1);
        assertFalse(audit.isOk());
        assertEquals(1, audit.getUnreconciled().size());
        assertEquals(s2.getStroke().getId(), audit.getUnreconciled().get(0).getStrokeId(),
                "核不出的正是被抹了痕的那笔");
        assertFalse(audit.getReconciled().contains(s2.getStroke().getId()), "那笔不作数");
        assertEquals(2, audit.getReconciled().size(), "其余两笔照样算数");
    }

    @Test
    @DisplayName("头表汇总数被改花（页面抢先亮的数）：抽验以册上为准点出来")
    void audit_tamperedHead_pageNumbersDontCount() {
        delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        delRuleService.sign(BILL, "柜员甲", "受理", at("09:12"), "S-1", null);
        delRuleService.sign(BILL, "单证员乙", "单证齐全", at("09:40"), "S-2", null);

        // 页面抢先亮：头表被写成已落 3 道
        THeriDelRule head = head(BILL);
        head.setNodeNo(3);
        delRuleMapper.updateById(head);

        DelAudit audit = delRuleService.audit(BILL, 1);
        assertFalse(audit.isOk());
        assertTrue(audit.getNotes().stream().anyMatch(n -> n.contains("页面上先亮出来的不算数")),
                String.join("；", audit.getNotes()));
    }

    @Test
    @DisplayName("随机抽一单倒着核：抽到的核得出；册上一单都没有就没的抽")
    void spotCheck_picksAndAudits() {
        assertTrue(delRuleService.spotCheck().isOk(), "空册没的抽也算过");

        delRuleService.open("NB-X1", new BigDecimal("300"), at("09:00"), "开单员");
        delRuleService.sign("NB-X1", "柜员甲", "受理", at("09:12"), "X1-1", null);
        delRuleService.open("NB-X2", new BigDecimal("300"), at("09:00"), "开单员");
        delRuleService.sign("NB-X2", "柜员甲", "受理", at("09:12"), "X2-1", null);
        delRuleService.sign("NB-X2", "单证员乙", "单证齐全", at("09:40"), "X2-2", null);

        DelAudit report = delRuleService.spotCheck();
        assertNotNull(report.getBillNo());
        assertTrue(report.isOk(), String.join("；", report.getNotes()));
        assertTrue("NB-X1".equals(report.getBillNo()) || "NB-X2".equals(report.getBillNo()));
    }

    // ---------------- 放货：关齐核发才放，各档有各档的走法 ----------------

    @Test
    @DisplayName("直发档当场放；换单档先换单；改提单档交上去核；没封口不能放；重放照面")
    void release_gradePreconditions() {
        delRuleService.setGradeGates(java.util.Collections.singletonMap("换单档", 2));
        delRuleService.setGradeGates(java.util.Collections.singletonMap("改提单档", 4));

        // 直发档：当场放
        delRuleService.open("NB-D1", new BigDecimal("300"), at("09:00"), "开单员");
        walkGates("NB-D1", 3);
        THeriDelRow d1 = delRuleService.release("NB-D1", "闸口丁", at("10:10"));
        assertEquals(Integer.valueOf(1), d1.getStatus(), "凭据已发");
        assertTrue(d1.getNotifyType().contains("当场放"));
        THeriDelRow d1again = delRuleService.release("NB-D1", "闸口丁", at("10:11"));
        assertEquals(d1.getId(), d1again.getId(), "重放只照面，不重复落");
        assertEquals(1, delRowMapper.selectCount(new QueryWrapper<THeriDelRow>()
                .eq("item_no", "R:NB-D1:1")));

        // 换单档：先换单才放
        delRuleService.open("NB-D2", new BigDecimal("1500"), at("09:00"), "开单员");
        walkGates("NB-D2", 2);
        IllegalStateException e1 = assertThrows(IllegalStateException.class,
                () -> delRuleService.release("NB-D2", "闸口丁", at("10:10")));
        assertTrue(e1.getMessage().contains("先换单"), e1.getMessage());
        delRuleService.confirm("NB-D2", "换单", "单证员乙", at("10:11"));
        assertNotNull(delRuleService.release("NB-D2", "闸口丁", at("10:12")));

        // 改提单档：交上去核才放
        delRuleService.open("NB-D3", new BigDecimal("3000"), at("09:00"), "开单员");
        walkGates("NB-D3", 4);
        IllegalStateException e2 = assertThrows(IllegalStateException.class,
                () -> delRuleService.release("NB-D3", "闸口丁", at("10:10")));
        assertTrue(e2.getMessage().contains("交上去核"), e2.getMessage());
        delRuleService.confirm("NB-D3", "交核", "值班领导丙", at("10:11"));
        assertNotNull(delRuleService.release("NB-D3", "闸口丁", at("10:12")));

        // 没关齐封口：不能放
        delRuleService.open("NB-D4", new BigDecimal("300"), at("09:00"), "开单员");
        delRuleService.sign("NB-D4", "柜员甲", "受理", at("09:12"), "D4-1", null);
        assertThrows(IllegalStateException.class,
                () -> delRuleService.release("NB-D4", "闸口丁", at("10:10")));
    }

    // ---------------- 同号再开只照面 ----------------

    @Test
    @DisplayName("同号再开只照面：不另立一单，册上只有一行头")
    void open_duplicateFaceOnly() {
        THeriDelRule first = delRuleService.open(BILL, new BigDecimal("300"), at("09:00"), "开单员");
        THeriDelRule again = delRuleService.open(BILL, new BigDecimal("300"), at("09:01"), "开单员");
        assertEquals(first.getId(), again.getId());
        assertEquals(1, delRuleMapper.selectCount(new QueryWrapper<THeriDelRule>()
                .eq("bill_no", BILL)));
    }

    // ---------------- helpers ----------------

    /** 顺次走完 n 道关（受理→单证齐全→结关×n-2） */
    private void walkGates(String bill, int gates) {
        String[] actors = {"柜员甲", "单证员乙", "闸口丁", "值班领导丙"};
        String[] actions = {"受理", "单证齐全", "结关", "结关"};
        for (int i = 1; i <= gates; i++) {
            delRuleService.sign(bill, actors[(i - 1) % actors.length],
                    actions[Math.min(i - 1, actions.length - 1)], at("09:1" + i), bill + "-W" + i, null);
        }
    }

    private THeriDelSign stroke(Long id) {
        return delSignMapper.selectById(id);
    }

    private THeriDelRule head(String bill) {
        return delRuleService.selectCurrent(bill);
    }

    private List<THeriDelSign> strokes(String bill, int round) {
        return delRuleService.listStrokes(bill, round);
    }

    private static int countByStatus(List<THeriDelSign> strokes, int status) {
        int n = 0;
        for (THeriDelSign s : strokes) {
            if (s.getStatus() != null && s.getStatus() == status) {
                n++;
            }
        }
        return n;
    }

    private static int effectiveAtGate(List<THeriDelSign> strokes, int gate) {
        int n = 0;
        for (THeriDelSign s : strokes) {
            if (s.getStatus() != null && s.getStatus() == STROKE_OK
                    && s.getGateNo() != null && s.getGateNo() == gate) {
                n++;
            }
        }
        return n;
    }

    private static THeriDelSign effectiveStrokeAtGate(List<THeriDelSign> strokes, int gate) {
        for (THeriDelSign s : strokes) {
            if (s.getStatus() != null && s.getStatus() == STROKE_OK
                    && s.getGateNo() != null && s.getGateNo() == gate) {
                return s;
            }
        }
        return null;
    }

    private long countTrail(String bill, String method) {
        long n = 0;
        for (TSysOperLog t : delRuleService.listTrail(bill)) {
            if (method.equals(t.getMethod())) {
                n++;
            }
        }
        return n;
    }

    private TSysOperLog trailOf(String bill, String method) {
        for (TSysOperLog t : delRuleService.listTrail(bill)) {
            if (method.equals(t.getMethod())) {
                return t;
            }
        }
        return null;
    }
}
