package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THeriDelRowMapper;
import com.fc.v2.mapper.auto.THeriDelRuleMapper;
import com.fc.v2.mapper.auto.THeriDelSignMapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriBlRule;
import com.fc.v2.model.auto.THeriDelRow;
import com.fc.v2.model.auto.THeriDelRule;
import com.fc.v2.model.auto.THeriDelSign;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.DelAudit;
import com.fc.v2.model.custom.DelStroke;
import com.fc.v2.service.ITHeriBlRuleService;
import com.fc.v2.service.ITHeriDelRuleService;
import com.fc.v2.util.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

/**
 * 港口放货台 Service实现（gate-ledger 形状：一道一道落签）。
 *
 * <p>落账结构（两边账本 + 一本册）：</p>
 * <ul>
 *   <li>t_heri_del_rule：凭证头，档和几道关开单时写死；node_no 只是页面先亮的汇总数，不作依据；</li>
 *   <li>t_heri_del_sign：签认册，关口每一笔（有效/作废/被拒）都留着，一道只容一笔有效；</li>
 *   <li>t_sys_oper_log：留痕册，谁落的、何时落的、退了几回、最后哪一道定的；</li>
 *   <li>关口那一步动两边账本：签认和留痕同一事务落，不能只落一处。</li>
 * </ul>
 *
 * <p>并发：每单一把锁排队（先落的那一笔算数）；baseStroke 认出抢着签的，
 * 后到的那一笔作废并记账；库上 uk(bill_no,round_no,eff_gate) 兜底一道一笔，
 * uk(bill_no,round_no,attempt_id) 兜底重办不重复落。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class THeriDelRuleServiceImpl implements ITHeriDelRuleService {

    // ---- 凭证头状态：0在办 1已核发(关齐封口) 2已封住(收口重来的老趟) ----
    private static final int HEAD_RUNNING = 0;
    private static final int HEAD_ISSUED = 1;
    private static final int HEAD_CLOSED = 2;

    // ---- 签认册：这笔算不算数 0有效 1作废 2被拒 ----
    private static final int STROKE_EFFECTIVE = 0;
    private static final int STROKE_VOIDED = 1;
    private static final int STROKE_REJECTED = 2;

    // ---- 作废缘由 ----
    private static final String VOID_SUPERSEDED = "被后一笔压下";
    private static final String VOID_RACE_LOST = "抢签落败";
    private static final String VOID_ROLLBACK = "回退冲下";

    // ---- 档 ----
    private static final String GRADE_DIRECT = "直发档";
    private static final String GRADE_EXCHANGE = "换单档";
    private static final String GRADE_AMEND = "改提单档";

    /** 每一单一把锁：同号核签排队等，异号各锁各的、互不踩 */
    private final ConcurrentHashMap<String, Object> segmentLocks = new ConcurrentHashMap<String, Object>();

    /** 港里判据：档→几道关。册上记录在案的默认只有直发档=3，其余由港里颁行补齐 */
    private final Map<String, Integer> gradeGates = new ConcurrentHashMap<String, Integer>();

    /** 册上发号（落笔幂等号） */
    private static final SnowflakeIdWorker ID_WORKER = new SnowflakeIdWorker(1, 1);

    @Resource
    private THeriDelRuleMapper heriDelRuleMapper;
    @Resource
    private THeriDelSignMapper heriDelSignMapper;
    @Resource
    private THeriDelRowMapper heriDelRowMapper;
    @Resource
    private TSysOperLogMapper sysOperLogMapper;
    @Resource
    private ITHeriBlRuleService heriBlRuleService;

    /** 落账必须成事务（签认和留痕同一次完成）；生产由容器配，测试夹具显式给 */
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    public THeriDelRuleServiceImpl() {
        // 册上记录在案：直发档走三道。其余档几道关由港里颁行（setGradeGates），没写到的退回重定
        this.gradeGates.put(GRADE_DIRECT, 3);
    }

    // ---------------- 开单分档 ----------------

    @Override
    public THeriDelRule open(String billNo, BigDecimal qty, Date at, String operator) {
        if (billNo == null || billNo.trim().isEmpty()) {
            throw new IllegalArgumentException("凭证代号没写");
        }
        if (qty == null) {
            throw retier("数量没写，定不了档");
        }
        final Date when = at != null ? at : new Date();
        synchronized (lockOf(billNo)) {
            THeriDelRule existing = selectCurrent(billNo);
            if (existing != null) {
                // 同号再开只照面，不另立
                return existing;
            }
            final String grade;
            try {
                grade = classify(qty, when);
            } catch (IllegalStateException e) {
                // 判据没写到的，退回重定，系统不自己挑；退回也留一笔痕
                writeTrailAlone(billNo, "退回重定", operator, e.getMessage() + "，数量=" + qty);
                throw e;
            }
            Integer gates = this.gradeGates.get(grade);
            if (gates == null) {
                IllegalStateException e = retier(grade + "走几道关，判据没写到");
                writeTrailAlone(billNo, "退回重定", operator, e.getMessage());
                throw e;
            }
            final String g = grade;
            final Integer n = gates;
            return inTx(new Supplier<THeriDelRule>() {
                @Override
                public THeriDelRule get() {
                    THeriDelRule head = new THeriDelRule();
                    head.setBillNo(billNo);
                    head.setGrade(g);
                    head.setGateTotal(n); // 几道关册上写死，往后加关口不追老账
                    head.setRoundNo(1);
                    head.setNodeNo(0);
                    head.setRollbackCnt(0);
                    head.setStatus(HEAD_RUNNING);
                    head.setDelFlag(0);
                    head.setCreateBy(operator);
                    head.setCreateTime(new Date());
                    heriDelRuleMapper.insert(head);
                    writeTrail(billNo, "开单", operator,
                            "趟=1｜档=" + g + "｜几道关=" + n + "｜数量=" + qty + "｜第1趟开张");
                    return head;
                }
            });
        }
    }

    /** 分档看数量：生效判据按优先级来，够到哪档归哪档；都没写到退回重定，系统不自己挑 */
    private String classify(BigDecimal qty, Date at) {
        List<THeriBlRule> rules = this.heriBlRuleService.listAvailable(at);
        for (THeriBlRule r : rules) {
            if (r.getTh1Max() != null && qty.compareTo(r.getTh1Max()) <= 0) {
                return GRADE_DIRECT;
            }
            if (r.getTh2Max() != null && qty.compareTo(r.getTh2Max()) <= 0) {
                return GRADE_EXCHANGE;
            }
            if (r.getTh3Max() != null && qty.compareTo(r.getTh3Max()) <= 0) {
                return GRADE_AMEND;
            }
        }
        throw retier("数量" + qty + "件判据没写到");
    }

    private static IllegalStateException retier(String why) {
        return new IllegalStateException("退回重定：" + why + "，系统不自己挑");
    }

    // ---------------- 落签 / 压签 ----------------

    @Override
    public DelStroke sign(final String billNo, final String signer, final String action, Date at,
                          String attemptId, final Long baseStroke) {
        checkPenReady(billNo, signer, action);
        final Date when = at != null ? at : new Date();
        final String attempt = blank(attemptId) ? "A" + ID_WORKER.nextId() : attemptId.trim();
        synchronized (lockOf(billNo)) {
            // 同一笔重办：册上已有原样返回（照面），只补没写的那几步，不重复落
            THeriDelSign dup = findByAttempt(billNo, attempt);
            if (dup != null) {
                return new DelStroke(DelStroke.OUT_FACE_ONLY, dup, "同一笔重办，照册上的来，不重复落");
            }
            try {
                return inTx(new Supplier<DelStroke>() {
                    @Override
                    public DelStroke get() {
                        THeriDelRule head = mustCurrent(billNo);
                        int k = effectiveCount(head); // 以册上为准：关数从签认册数出来，不信页面先亮的
                        if (head.getStatus() != null && head.getStatus() == HEAD_ISSUED) {
                            // 关齐即封：多的一道不算数，痕留着（册上三道、系统走了四道，以册上为准）
                            THeriDelSign rej = newStroke(head, k + 1, signer, action, when, attempt);
                            rej.setStatus(STROKE_REJECTED);
                            rej.setRejectReason("册上" + head.getGateTotal() + "道已封口，多了一道，以册上为准");
                            heriDelSignMapper.insert(rej);
                            writeTrail(billNo, "被拒", signer,
                                    leg(head) + pen(rej.getId()) + "关=" + rej.getGateNo() + "｜"
                                            + rej.getRejectReason());
                            return new DelStroke(DelStroke.OUT_REJECTED, rej, rej.getRejectReason());
                        }
                        // 抢着签：签的人看到的那笔跟册上对不上——先落的算数，这笔作废并记账
                        THeriDelSign last = lastEffective(head);
                        if (baseStroke != null && !sameBase(baseStroke, last)) {
                            THeriDelSign v = newStroke(head, k + 1, signer, action, when, attempt);
                            v.setStatus(STROKE_VOIDED);
                            v.setVoidReason(VOID_RACE_LOST);
                            heriDelSignMapper.insert(v);
                            writeTrail(billNo, "作废", signer,
                                    leg(head) + pen(v.getId()) + "关=" + v.getGateNo() + "｜" + VOID_RACE_LOST
                                            + "：先落的那一笔算数，这笔后到作废，痕留着");
                            return new DelStroke(DelStroke.OUT_VOIDED, v,
                                    "抢着签：先落的那一笔算数，这笔作废留痕");
                        }
                        // 顺次落下一道：关次系统从上往下排，不许手填
                        THeriDelSign s = newStroke(head, k + 1, signer, action, when, attempt);
                        s.setStatus(STROKE_EFFECTIVE);
                        s.setEffGate(k + 1);
                        heriDelSignMapper.insert(s);
                        writeTrail(billNo, "落签", signer,
                                leg(head) + pen(s.getId()) + "关=" + s.getGateNo() + "｜动作=" + action
                                        + "｜册上时刻=" + fmt(when));
                        head.setNodeNo(k + 1);
                        if (k + 1 >= head.getGateTotal()) {
                            // 关齐即封口：最后哪一道定的写进留痕
                            head.setStatus(HEAD_ISSUED);
                            touch(head, signer);
                            heriDelRuleMapper.updateById(head);
                            writeTrail(billNo, "封口", signer,
                                    leg(head) + "关齐：第" + (k + 1) + "道落定（" + signer + "，" + fmt(when)
                                            + "），此单封口；最后哪一道定的=第" + (k + 1) + "道");
                        } else {
                            touch(head, signer);
                            heriDelRuleMapper.updateById(head);
                        }
                        return new DelStroke(DelStroke.OUT_SIGN, s,
                                "第" + s.getGateNo() + "道落定：" + signer + " " + action);
                    }
                });
            } catch (DuplicateKeyException e) {
                // 库上兜底：同一笔重办撞了唯一键，照面返回册上那笔
                THeriDelSign dup2 = findByAttempt(billNo, attempt);
                if (dup2 != null) {
                    return new DelStroke(DelStroke.OUT_FACE_ONLY, dup2, "同一笔重办，照册上的来，不重复落");
                }
                throw e;
            }
        }
    }

    @Override
    public DelStroke resign(final String billNo, final String signer, final String action, Date at,
                            String attemptId, final Long baseStroke) {
        checkPenReady(billNo, signer, action);
        final Date when = at != null ? at : new Date();
        final String attempt = blank(attemptId) ? "A" + ID_WORKER.nextId() : attemptId.trim();
        synchronized (lockOf(billNo)) {
            THeriDelSign dup = findByAttempt(billNo, attempt);
            if (dup != null) {
                return new DelStroke(DelStroke.OUT_FACE_ONLY, dup, "同一笔重办，照册上的来，不重复落");
            }
            try {
                return inTx(new Supplier<DelStroke>() {
                    @Override
                    public DelStroke get() {
                        THeriDelRule head = mustCurrent(billNo);
                        int k = effectiveCount(head);
                        if (head.getStatus() != null && head.getStatus() == HEAD_ISSUED) {
                            // 关齐即封：要加签，先把整件事收口重来
                            THeriDelSign rej = newStroke(head, k, signer, action, when, attempt);
                            rej.setStatus(STROKE_REJECTED);
                            rej.setRejectReason("已封口，不许再往上加签；要加签先把整件事收口重来");
                            heriDelSignMapper.insert(rej);
                            writeTrail(billNo, "被拒", signer,
                                    leg(head) + pen(rej.getId()) + "关=" + k + "｜" + rej.getRejectReason());
                            return new DelStroke(DelStroke.OUT_REJECTED, rej, rej.getRejectReason());
                        }
                        if (k == 0) {
                            throw new IllegalStateException("还没落过笔，没有可重签的道");
                        }
                        final THeriDelSign hanging = lastEffective(head); // 悬着的那道（还没定下来）
                        if (baseStroke != null && !baseStroke.equals(hanging.getId())) {
                            // 抢着压签：先落的算数，后到的作废并记账
                            THeriDelSign v = newStroke(head, k, signer, action, when, attempt);
                            v.setStatus(STROKE_VOIDED);
                            v.setVoidReason(VOID_RACE_LOST);
                            heriDelSignMapper.insert(v);
                            writeTrail(billNo, "作废", signer,
                                    leg(head) + pen(v.getId()) + "关=" + k + "｜" + VOID_RACE_LOST
                                            + "：先落的那一笔算数，这笔后到作废，痕留着");
                            return new DelStroke(DelStroke.OUT_VOIDED, v,
                                    "抢着签：先落的那一笔算数，这笔作废留痕");
                        }
                        // 后一笔压前一笔：前一笔作废留痕（不许悄悄抹掉），新笔有效并指回去
                        hanging.setStatus(STROKE_VOIDED);
                        hanging.setVoidReason(VOID_SUPERSEDED);
                        hanging.setEffGate(null); // 作废不占关次位，一道只容一笔有效
                        touch(hanging, signer);
                        heriDelSignMapper.updateById(hanging);
                        THeriDelSign s = newStroke(head, k, signer, action, when, attempt);
                        s.setStatus(STROKE_EFFECTIVE);
                        s.setEffGate(k);
                        s.setSupersedes(hanging.getId());
                        heriDelSignMapper.insert(s);
                        writeTrail(billNo, "压签", signer,
                                leg(head) + pen(s.getId()) + "关=" + k + "｜动作=" + action
                                        + "｜压了哪一笔=" + hanging.getId() + "｜册上时刻=" + fmt(when));
                        writeTrail(billNo, "作废", signer,
                                leg(head) + pen(hanging.getId()) + "关=" + k + "｜" + VOID_SUPERSEDED
                                        + "，痕留着不许抹");
                        return new DelStroke(DelStroke.OUT_RESIGN, s,
                                "第" + k + "道重签：" + signer + " " + action + "，前一笔作废留痕");
                    }
                });
            } catch (DuplicateKeyException e) {
                THeriDelSign dup2 = findByAttempt(billNo, attempt);
                if (dup2 != null) {
                    return new DelStroke(DelStroke.OUT_FACE_ONLY, dup2, "同一笔重办，照册上的来，不重复落");
                }
                throw e;
            }
        }
    }

    // ---------------- 回退 / 收口重来 ----------------

    @Override
    public DelStroke rollback(final String billNo, final String operator, Date at, final String reason) {
        if (blank(operator)) {
            throw new IllegalArgumentException("谁退的必须写");
        }
        final Date when = at != null ? at : new Date();
        synchronized (lockOf(billNo)) {
            return inTx(new Supplier<DelStroke>() {
                @Override
                public DelStroke get() {
                    THeriDelRule head = mustCurrent(billNo);
                    int k = effectiveCount(head);
                    if (head.getStatus() != null && head.getStatus() == HEAD_ISSUED) {
                        // 关齐即封：不许退；要动先把整件事收口重来
                        THeriDelSign rej = newStroke(head, k, operator, "回退", when,
                                "A" + ID_WORKER.nextId());
                        rej.setStatus(STROKE_REJECTED);
                        rej.setRejectReason("已封口，不许退；要动先把整件事收口重来");
                        heriDelSignMapper.insert(rej);
                        writeTrail(billNo, "被拒", operator,
                                leg(head) + pen(rej.getId()) + "关=" + k + "｜" + rej.getRejectReason());
                        return new DelStroke(DelStroke.OUT_REJECTED, rej, rej.getRejectReason());
                    }
                    if (k == 0) {
                        throw new IllegalStateException("一道都没落，没的可退");
                    }
                    // 只退悬着的那一道；已定的那几段原地不动，也不跟着回来
                    THeriDelSign hanging = lastEffective(head);
                    hanging.setStatus(STROKE_VOIDED);
                    hanging.setVoidReason(VOID_ROLLBACK);
                    hanging.setEffGate(null); // 退下的不占关次位
                    touch(hanging, operator);
                    heriDelSignMapper.updateById(hanging);
                    int backs = (head.getRollbackCnt() == null ? 0 : head.getRollbackCnt()) + 1;
                    head.setRollbackCnt(backs); // 退了几回，记一回
                    head.setNodeNo(k - 1);
                    touch(head, operator);
                    heriDelRuleMapper.updateById(head);
                    writeTrail(billNo, "回退", operator,
                            leg(head) + pen(hanging.getId()) + "关=" + k + "｜" + VOID_ROLLBACK + "｜退到第"
                                    + (k - 1) + "道｜第" + backs + "回退｜已定的那几段原地不动"
                                    + (blank(reason) ? "" : "｜" + reason));
                    return new DelStroke(DelStroke.OUT_ROLLBACK, hanging,
                            "悬着的第" + k + "道退下留痕（第" + backs + "回退），前头已定的原地不动");
                }
            });
        }
    }

    @Override
    public THeriDelRule redo(final String billNo, final String operator, Date at) {
        if (blank(operator)) {
            throw new IllegalArgumentException("谁办的必须写");
        }
        synchronized (lockOf(billNo)) {
            return inTx(new Supplier<THeriDelRule>() {
                @Override
                public THeriDelRule get() {
                    THeriDelRule head = mustCurrent(billNo);
                    if (head.getStatus() == null || head.getStatus() != HEAD_ISSUED) {
                        throw new IllegalStateException("还没关齐封口，谈不上收口重来");
                    }
                    // 老趟收口封住：一条不动，照旧查得回
                    head.setStatus(HEAD_CLOSED);
                    touch(head, operator);
                    heriDelRuleMapper.updateById(head);
                    writeTrail(billNo, "收口", operator,
                            leg(head) + "第" + head.getRoundNo() + "趟关齐收口，老趟一条不动，照旧查得回");
                    // 整件事重来：档和几道关照册上写死的来，开新一趟
                    THeriDelRule fresh = new THeriDelRule();
                    fresh.setBillNo(billNo);
                    fresh.setGrade(head.getGrade());
                    fresh.setGateTotal(head.getGateTotal());
                    fresh.setRoundNo(head.getRoundNo() + 1);
                    fresh.setNodeNo(0);
                    fresh.setRollbackCnt(0);
                    fresh.setStatus(HEAD_RUNNING);
                    fresh.setDelFlag(0);
                    fresh.setCreateBy(operator);
                    fresh.setCreateTime(new Date());
                    heriDelRuleMapper.insert(fresh);
                    writeTrail(billNo, "重来", operator,
                            leg(fresh) + "整件事收口重来，开第" + fresh.getRoundNo() + "趟；档="
                                    + head.getGrade() + "，几道关=" + head.getGateTotal()
                                    + "（册上写死的，不替人重定）");
                    return fresh;
                }
            });
        }
    }

    // ---------------- 放货 ----------------

    @Override
    public THeriDelRule confirm(final String billNo, final String kind, final String operator, Date at) {
        if (blank(kind)) {
            throw new IllegalArgumentException("确认的是哪一档事必须写（换单/交核）");
        }
        synchronized (lockOf(billNo)) {
            return inTx(new Supplier<THeriDelRule>() {
                @Override
                public THeriDelRule get() {
                    THeriDelRule head = mustCurrent(billNo);
                    writeTrail(billNo, kind + "确认", operator,
                            "趟=" + head.getRoundNo() + "｜" + kind + "确认落痕");
                    return head;
                }
            });
        }
    }

    @Override
    public THeriDelRow release(final String billNo, final String operator, Date at) {
        synchronized (lockOf(billNo)) {
            return inTx(new Supplier<THeriDelRow>() {
                @Override
                public THeriDelRow get() {
                    THeriDelRule head = mustCurrent(billNo);
                    if (head.getStatus() == null || head.getStatus() != HEAD_ISSUED) {
                        throw new IllegalStateException("还没关齐封口，不能放");
                    }
                    String itemNo = "R:" + billNo + ":" + head.getRoundNo();
                    THeriDelRow exist = findDelRow(itemNo);
                    if (exist != null) {
                        // 重放只照面，不重复落
                        return exist;
                    }
                    // 分档口径：直发档当场放；换单档先换单；改提单档交上去核
                    String how;
                    if (GRADE_EXCHANGE.equals(head.getGrade())) {
                        requireConfirm(billNo, head.getRoundNo(), "换单", "换单档先换单：册上没有换单确认，不放");
                        how = "先换单后放";
                    } else if (GRADE_AMEND.equals(head.getGrade())) {
                        requireConfirm(billNo, head.getRoundNo(), "交核", "改提单档交上去核：册上没有交核确认，不放");
                        how = "交核后放";
                    } else {
                        how = "当场放";
                    }
                    THeriDelRow row = new THeriDelRow();
                    row.setItemNo(itemNo);
                    row.setNotifyType(head.getGrade() + how);
                    row.setStatus(1); // 已发
                    row.setDelFlag(0);
                    row.setCreateBy(operator);
                    row.setCreateTime(new Date());
                    heriDelRowMapper.insert(row);
                    writeTrail(billNo, "放货", operator,
                            "趟=" + head.getRoundNo() + "｜档=" + head.getGrade() + "｜" + how + "｜凭据=" + itemNo);
                    return row;
                }
            });
        }
    }

    // ---------------- 查 ----------------

    @Override
    public THeriDelRule selectTHeriDelRuleById(Long id) {
        if (id == null) {
            return null;
        }
        return this.heriDelRuleMapper.selectById(id);
    }

    @Override
    public THeriDelRule selectCurrent(String billNo) {
        if (blank(billNo)) {
            return null;
        }
        return this.heriDelRuleMapper.selectOne(new QueryWrapper<THeriDelRule>()
                .eq("bill_no", billNo).eq("del_flag", 0)
                .orderByDesc("round_no").last("limit 1"));
    }

    @Override
    public THeriDelRule selectRound(String billNo, Integer roundNo) {
        if (blank(billNo) || roundNo == null) {
            return null;
        }
        return this.heriDelRuleMapper.selectOne(new QueryWrapper<THeriDelRule>()
                .eq("bill_no", billNo).eq("round_no", roundNo).eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public List<THeriDelSign> listStrokes(String billNo, Integer roundNo) {
        if (blank(billNo)) {
            return new ArrayList<THeriDelSign>();
        }
        QueryWrapper<THeriDelSign> q = new QueryWrapper<THeriDelSign>()
                .eq("bill_no", billNo).eq("del_flag", 0).orderByAsc("id");
        if (roundNo != null) {
            q.eq("round_no", roundNo);
        }
        return this.heriDelSignMapper.selectList(q);
    }

    @Override
    public List<TSysOperLog> listTrail(String billNo) {
        if (blank(billNo)) {
            return new ArrayList<TSysOperLog>();
        }
        return this.sysOperLogMapper.selectList(new QueryWrapper<TSysOperLog>()
                .eq("oper_url", trailKey(billNo)).orderByAsc("oper_time").orderByAsc("id"));
    }

    // ---------------- 抽验：随机抽一单倒着核 ----------------

    @Override
    public DelAudit audit(String billNo, Integer roundNo) {
        THeriDelRule head = selectRound(billNo, roundNo);
        if (head == null) {
            throw new IllegalArgumentException("单不在册上：" + billNo + " 第" + roundNo + "趟");
        }
        List<THeriDelSign> strokes = listStrokes(billNo, roundNo);
        // 留痕按趟归：只核这一趟的痕，前趟后趟互不串
        List<TSysOperLog> trails = new ArrayList<TSysOperLog>();
        for (TSysOperLog t : listTrail(billNo)) {
            if (t.getOperParam() != null && t.getOperParam().contains("趟=" + roundNo + "｜")) {
                trails.add(t);
            }
        }
        DelAudit report = new DelAudit();
        report.setBillNo(billNo);
        report.setRoundNo(roundNo);

        List<THeriDelSign> effective = new ArrayList<THeriDelSign>();
        List<THeriDelSign> voided = new ArrayList<THeriDelSign>();
        List<THeriDelSign> rejected = new ArrayList<THeriDelSign>();
        for (THeriDelSign s : strokes) {
            if (s.getStatus() == null) {
                continue;
            }
            if (s.getStatus() == STROKE_EFFECTIVE) {
                effective.add(s);
            } else if (s.getStatus() == STROKE_VOIDED) {
                voided.add(s);
            } else if (s.getStatus() == STROKE_REJECTED) {
                rejected.add(s);
            }
        }
        // 倒着核：有效笔按关次从大到小排，从最后落定的那道往回数
        List<THeriDelSign> desc = new ArrayList<THeriDelSign>(effective);
        desc.sort((a, b) -> (b.getEffGate() == null ? 0 : b.getEffGate())
                - (a.getEffGate() == null ? 0 : a.getEffGate()));

        // 1) 关次连号：从最后那道往回数一道不缺（数与次对不上，以册上为准）
        int expect = effective.size();
        Map<Integer, Integer> gateUse = new HashMap<Integer, Integer>();
        for (THeriDelSign e : desc) {
            Integer g = e.getEffGate();
            if (g == null || g != expect) {
                miss(report, e.getId(), "关次断号：这笔是第" + g + "道，倒着数该是第" + expect + "道");
            }
            gateUse.put(g, gateUse.get(g) == null ? 1 : gateUse.get(g) + 1);
            expect--;
        }
        // 一道只容一笔
        for (Map.Entry<Integer, Integer> en : gateUse.entrySet()) {
            if (en.getValue() != null && en.getValue() > 1) {
                miss(report, null, "第" + en.getKey() + "道落了" + en.getValue() + "笔有效，一道只容一笔");
            }
        }
        // 2) 每笔有效签认要有对应留痕（两边账本对得上，核不出的不作数）
        for (THeriDelSign e : effective) {
            if (!hasTrail(trails, "落签", e.getId()) && !hasTrail(trails, "压签", e.getId())) {
                miss(report, e.getId(), "签认册有这笔（第" + e.getGateNo() + "道 " + e.getSigner()
                        + "），留痕册核不出对应落笔痕——不作数");
            }
        }
        // 3) 作废的每笔：要有作废痕；说是被压下的，册上要找得到压它的那笔
        for (THeriDelSign v : voided) {
            if (!hasTrail(trails, "作废", v.getId()) && !hasTrail(trails, "回退", v.getId())) {
                miss(report, v.getId(), "作废的这笔（第" + v.getGateNo() + "道）核不出作废痕——不许悄悄抹掉");
            }
            if (VOID_SUPERSEDED.equals(v.getVoidReason()) && !hasSuperseder(strokes, v.getId())) {
                miss(report, v.getId(), "说是被后一笔压下，册上找不到压它的那笔");
            }
        }
        // 4) 被拒的每笔：要有被拒痕
        for (THeriDelSign r : rejected) {
            if (!hasTrail(trails, "被拒", r.getId())) {
                miss(report, r.getId(), "被拒的这笔（第" + r.getGateNo() + "道）核不出被拒痕");
            }
        }
        // 5) 退了几回：头表、回退痕、回退冲下的笔，三处对得上
        int backs = 0;
        for (THeriDelSign v : voided) {
            if (VOID_ROLLBACK.equals(v.getVoidReason())) {
                backs++;
            }
        }
        int backTrails = countTrail(trails, "回退");
        int headBacks = head.getRollbackCnt() == null ? 0 : head.getRollbackCnt();
        if (headBacks != backs || headBacks != backTrails) {
            miss(report, null, "退了几回对不上：头表记" + headBacks + "回，签认册回退冲下" + backs
                    + "笔，留痕册回退痕" + backTrails + "笔");
        }
        // 6) 页面先亮的不算数：头表汇总数跟册上对不上，以册上为准
        int headNode = head.getNodeNo() == null ? 0 : head.getNodeNo();
        if (headNode != effective.size()) {
            miss(report, null, "头表亮着已落" + headNode + "道，册上有效" + effective.size()
                    + "道——页面上先亮出来的不算数，以册上为准");
        }
        // 7) 关齐封口：已核发/已封住（收口的老趟）⟺ 册上关数与档写死的对得上 + 封口痕在
        boolean sealedTrail = hasTrail(trails, "封口", null);
        if (head.getStatus() != null && (head.getStatus() == HEAD_ISSUED || head.getStatus() == HEAD_CLOSED)) {
            if (effective.size() != head.getGateTotal()) {
                miss(report, null, "已核发但册上有效" + effective.size() + "道，档上写死"
                        + head.getGateTotal() + "道，数与次对不上");
            }
            if (!sealedTrail) {
                miss(report, null, "已核发但留痕册核不出封口痕");
            }
        } else if (head.getStatus() != null && head.getStatus() == HEAD_RUNNING && sealedTrail) {
            miss(report, null, "还在办却已有封口痕，对不上");
        }
        // 8) 多出的关：关次超出档上写死的，必须都是被拒笔（这趟不算数）
        for (THeriDelSign s : strokes) {
            if (s.getGateNo() != null && head.getGateTotal() != null
                    && s.getGateNo() > head.getGateTotal()
                    && (s.getStatus() == null || s.getStatus() != STROKE_REJECTED)) {
                miss(report, s.getId(), "册上" + head.getGateTotal() + "道，第" + s.getGateNo()
                        + "道多出来却不是被拒笔——以册上为准，这趟不算数");
            }
        }
        // 核得出的算数
        List<Long> bad = new ArrayList<Long>();
        for (DelAudit.Miss m : report.getUnreconciled()) {
            if (m.getStrokeId() != null) {
                bad.add(m.getStrokeId());
            }
        }
        List<Long> good = new ArrayList<Long>();
        for (THeriDelSign s : strokes) {
            if (!bad.contains(s.getId())) {
                good.add(s.getId());
            }
        }
        report.setReconciled(good);
        report.setOk(report.getUnreconciled().isEmpty());
        if (report.isOk()) {
            report.getNotes().add("倒着核了一遍：关次连号、每笔有痕、退了几回对得上，全算数");
        }
        return report;
    }

    @Override
    public DelAudit spotCheck() {
        List<THeriDelRule> heads = this.heriDelRuleMapper.selectList(new QueryWrapper<THeriDelRule>()
                .eq("del_flag", 0));
        if (heads.isEmpty()) {
            DelAudit empty = new DelAudit();
            empty.setOk(true);
            empty.getNotes().add("册上一单都没有，没的抽");
            return empty;
        }
        // 随机抽一单倒着核
        THeriDelRule picked = heads.get(new Random().nextInt(heads.size()));
        return audit(picked.getBillNo(), picked.getRoundNo());
    }

    // ---------------- 港里判据：档→几道关 ----------------

    @Override
    public Map<String, Integer> gradeGates() {
        return new HashMap<String, Integer>(this.gradeGates);
    }

    @Override
    public void setGradeGates(Map<String, Integer> gates) {
        if (gates != null) {
            // 颁行/改判据只影响往后开的单；老单照开单时写死的走，不追老账
            this.gradeGates.putAll(gates);
        }
    }

    // ---------------- 零碎口径 ----------------

    /** 以册上为准：已落成关数从签认册数出来，不信头表页面先亮的 */
    private int effectiveCount(THeriDelRule head) {
        Integer cnt = this.heriDelSignMapper.selectCount(new QueryWrapper<THeriDelSign>()
                .eq("bill_no", head.getBillNo()).eq("round_no", head.getRoundNo())
                .eq("status", STROKE_EFFECTIVE).eq("del_flag", 0));
        return cnt == null ? 0 : cnt;
    }

    /** 悬着的那一道：册上最后一笔有效签（还没定下来的）；已定的那几段在它前头，原地不动 */
    private THeriDelSign lastEffective(THeriDelRule head) {
        return this.heriDelSignMapper.selectOne(new QueryWrapper<THeriDelSign>()
                .eq("bill_no", head.getBillNo()).eq("round_no", head.getRoundNo())
                .eq("status", STROKE_EFFECTIVE).eq("del_flag", 0)
                .orderByDesc("gate_no").last("limit 1"));
    }

    /** 签的人当时看到的那笔，跟册上最后一笔对不对得上（0=看到册上一笔都没有） */
    private static boolean sameBase(Long baseStroke, THeriDelSign last) {
        if (baseStroke != null && baseStroke == 0L) {
            return last == null;
        }
        return last != null && baseStroke != null && baseStroke.equals(last.getId());
    }

    private THeriDelRule mustCurrent(String billNo) {
        THeriDelRule head = selectCurrent(billNo);
        if (head == null) {
            throw new IllegalArgumentException("单不在册上：" + billNo);
        }
        if (head.getStatus() != null && head.getStatus() == HEAD_CLOSED) {
            // 老单子一条不动：写操作只许落在当前趟
            throw new IllegalStateException("老趟已封住，一条不动：" + billNo);
        }
        return head;
    }

    private THeriDelSign findByAttempt(String billNo, String attemptId) {
        return this.heriDelSignMapper.selectOne(new QueryWrapper<THeriDelSign>()
                .eq("bill_no", billNo).eq("attempt_id", attemptId).eq("del_flag", 0)
                .orderByDesc("round_no").last("limit 1"));
    }

    private THeriDelRow findDelRow(String itemNo) {
        return this.heriDelRowMapper.selectOne(new QueryWrapper<THeriDelRow>()
                .eq("item_no", itemNo).eq("del_flag", 0).last("limit 1"));
    }

    private void requireConfirm(String billNo, Integer roundNo, String kind, String msg) {
        for (TSysOperLog t : listTrail(billNo)) {
            if (t.getMethod() != null && t.getMethod().equals(kind + "确认")
                    && t.getOperParam() != null && t.getOperParam().contains("趟=" + roundNo + "｜")) {
                return;
            }
        }
        throw new IllegalStateException(msg);
    }

    private static THeriDelSign newStroke(THeriDelRule head, int gateNo, String signer,
                                          String action, Date when, String attempt) {
        THeriDelSign s = new THeriDelSign();
        s.setBillNo(head.getBillNo());
        s.setRoundNo(head.getRoundNo());
        s.setGateNo(gateNo); // 关次系统排，不许手填
        s.setSigner(signer);
        s.setAction(action);
        s.setSignedAt(when);
        s.setAttemptId(attempt);
        s.setDelFlag(0);
        s.setCreateBy(signer);
        s.setCreateTime(new Date());
        return s;
    }

    private static boolean hasSuperseder(List<THeriDelSign> strokes, Long voidedId) {
        for (THeriDelSign s : strokes) {
            if (voidedId != null && voidedId.equals(s.getSupersedes())) {
                return true;
            }
        }
        return false;
    }

    /** 留痕册里核某一笔的某种痕（按"笔=id｜"对） */
    private static boolean hasTrail(List<TSysOperLog> trails, String method, Long strokeId) {
        for (TSysOperLog t : trails) {
            if (t.getMethod() == null || !t.getMethod().equals(method)) {
                continue;
            }
            if (strokeId == null) {
                return true;
            }
            if (t.getOperParam() != null && t.getOperParam().contains(pen(strokeId))) {
                return true;
            }
        }
        return false;
    }

    private static int countTrail(List<TSysOperLog> trails, String method) {
        int n = 0;
        for (TSysOperLog t : trails) {
            if (t.getMethod() != null && t.getMethod().equals(method)) {
                n++;
            }
        }
        return n;
    }

    private static void miss(DelAudit report, Long strokeId, String reason) {
        report.getUnreconciled().add(new DelAudit.Miss(strokeId, reason));
        report.getNotes().add(reason);
    }

    private static String pen(Long strokeId) {
        return "笔=" + strokeId + "｜";
    }

    /** 留痕按趟归：每条痕起头带"趟=N｜"，抽验按趟对账，前趟后趟互不串 */
    private static String leg(THeriDelRule head) {
        return "趟=" + head.getRoundNo() + "｜";
    }

    private Object lockOf(String billNo) {
        // putIfAbsent 保证同号同一把锁；锁对象常驻（凭证号数量有限），异号各拿各的
        Object exist = this.segmentLocks.get(billNo);
        if (exist != null) {
            return exist;
        }
        Object fresh = new Object();
        Object old = this.segmentLocks.putIfAbsent(billNo, fresh);
        return old == null ? fresh : old;
    }

    /** 两边账本同一事务落：签认和留痕必须同一次完成，不能只落一处 */
    private <T> T inTx(Supplier<T> body) {
        if (this.transactionManager == null) {
            throw new IllegalStateException("落账必须成事务：transactionManager 未配"
                    + "（签认和留痕必须同一次完成，不能只落一处）");
        }
        return new TransactionTemplate(this.transactionManager).execute(status -> body.get());
    }

    /** 退回重定的痕：单还没立起来，留痕册单独记一笔（谁被退、为什么退） */
    private void writeTrailAlone(final String billNo, final String method, final String operator,
                                 final String param) {
        inTx(new Supplier<Object>() {
            @Override
            public Object get() {
                writeTrail(billNo, method, operator, "趟=-｜" + param);
                return null;
            }
        });
    }

    private void writeTrail(String billNo, String method, String operator, String param) {
        TSysOperLog log = new TSysOperLog();
        log.setTitle("放货核发");
        log.setMethod(method);
        log.setOperName(operator);
        log.setOperUrl(trailKey(billNo));
        log.setOperParam(param);
        log.setOperTime(new Date());
        this.sysOperLogMapper.insert(log);
    }

    private String trailKey(String billNo) {
        return "/heri/del/" + billNo;
    }

    private static void touch(THeriDelRule head, String operator) {
        head.setUpdateBy(operator);
        head.setUpdateTime(new Date());
    }

    private static void touch(THeriDelSign s, String operator) {
        s.setUpdateBy(operator);
        s.setUpdateTime(new Date());
    }

    private static void checkPenReady(String billNo, String signer, String action) {
        if (blank(billNo)) {
            throw new IllegalArgumentException("凭证代号没写");
        }
        if (blank(signer)) {
            throw new IllegalArgumentException("谁落的必须写");
        }
        if (blank(action)) {
            throw new IllegalArgumentException("关口动作没写（受理/单证齐全/结关）");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String fmt(Date d) {
        return d == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }
}
