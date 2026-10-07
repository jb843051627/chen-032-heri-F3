package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THeriDelBillMapper;
import com.fc.v2.mapper.auto.THeriDelGateMapper;
import com.fc.v2.mapper.auto.THeriDelGradeMapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriDelBill;
import com.fc.v2.model.auto.THeriDelGate;
import com.fc.v2.model.auto.THeriDelGrade;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.DelAuditResult;
import com.fc.v2.model.custom.DelOpenCmd;
import com.fc.v2.model.custom.DelSignReceipt;
import com.fc.v2.service.ITHeriReleaseService;
import com.fc.v2.util.SnowflakeIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 港口放货台 Service实现（gate-chain 形状：一票货按档过几道关）。
 *
 * <p>一句话口径：分档看数量，几道关档说了算、立册誊死；一道挨一道走，一道只容一笔；
 * 悬着的关后落笔压前笔（09:40/09:41），真同时抢签先落的算数（按 land_seq 错开）；
 * 末道首签即关齐封口，封口后加签/多过道一律不作数（10:05/10:06，以册上为准）；
 * 回退只摆悬着没定的段；要动封口件先收口、另起一遍，老账一条不动。</p>
 *
 * <p>两道账本：签认落 t_heri_del_gate（append-only，作废也留痕），经手留痕落
 * t_sys_oper_log；同一笔里两处一起落齐（trail_ok=1）才算数，中途出错停下，
 * 重办只把没写的那步补上。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class THeriReleaseServiceImpl implements ITHeriReleaseService {

    /** 册态：0在办 */
    private static final int BILL_RUNNING = 0;
    /** 册态：1已封住(关齐即封) */
    private static final int BILL_SEALED = 1;

    private static final int GRADE_ON = 0;

    private static final SnowflakeIdWorker ID_WORKER = new SnowflakeIdWorker(2, 2);

    /** 单号一段锁：同号排队错开，异号各签各的互不踩（真同时抢签拿它分先后） */
    private final ConcurrentHashMap<String, Object> billLocks = new ConcurrentHashMap<String, Object>();

    /** 落笔全局次序：同一刻抢签，序号小的先落、算数 */
    private final AtomicLong landSeq = new AtomicLong(0L);

    @Resource
    private THeriDelGradeMapper delGradeMapper;
    @Resource
    private THeriDelBillMapper delBillMapper;
    @Resource
    private THeriDelGateMapper delGateMapper;
    @Resource
    private TSysOperLogMapper sysOperLogMapper;

    @Override
    public THeriDelBill selectByBillNo(String billNo) {
        if (billNo == null) {
            return null;
        }
        return this.delBillMapper.selectOne(new QueryWrapper<THeriDelBill>()
                .eq("bill_no", billNo).eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public THeriDelBill openBill(DelOpenCmd cmd, String operator) {
        if (cmd == null || cmd.getQty() == null || cmd.getQty().signum() < 0) {
            throw new IllegalArgumentException("数量空着或是负数，立不了册，退回重定");
        }
        String billNo = trim(cmd.getBillNo());
        if (billNo != null) {
            THeriDelBill face = selectByBillNo(billNo);
            if (face != null) {
                // 同号再立：照面，一个字都不改
                return face;
            }
        }
        THeriDelGrade grade = pickGrade(cmd.getQty());
        if (grade == null) {
            // 判据没写到的：退回重定，系统绝不自己挑一档
            throw new IllegalArgumentException(
                    "数量 " + cmd.getQty().stripTrailingZeros().toPlainString()
                            + " 港里判据没写到，退回重定，系统不自己挑档");
        }
        if (grade.getGates() == null || grade.getGates().intValue() < 1) {
            throw new IllegalArgumentException(
                    "档 " + grade.getGradeCode() + " 没写几道关，退回重定");
        }
        if (billNo == null) {
            billNo = "NB" + new java.text.SimpleDateFormat("yyyyMMdd").format(new Date())
                    + ID_WORKER.nextId();
        }
        final String no = billNo;
        synchronized (lockOf(no)) {
            THeriDelBill again = selectByBillNo(no);
            if (again != null) {
                return again;
            }
            Date now = new Date();
            THeriDelBill bill = new THeriDelBill();
            bill.setBillNo(no);
            // 归哪一档、几道关，立册写死：往后判据改了、加关口了，不追老账
            bill.setGradeCode(grade.getGradeCode());
            bill.setGradeName(grade.getGradeName());
            bill.setBizMode(grade.getBizMode());
            bill.setQty(cmd.getQty());
            bill.setGateCount(grade.getGates());
            bill.setRoundNo(1);
            bill.setStatus(BILL_RUNNING);
            bill.setCurGate(1);
            bill.setRollbackCnt(0);
            bill.setDelFlag(0);
            bill.setCreateBy(operator);
            bill.setCreateTime(now);
            this.delBillMapper.insert(bill);
            writeTrail(no, 1, "立册", operator,
                    "数量=" + cmd.getQty().stripTrailingZeros().toPlainString()
                            + "，判据定档=" + grade.getGradeName()
                            + "，册上写死 " + grade.getGates() + " 道关，从头一道走起");
            return bill;
        }
    }

    /** 分档看数量：启用判据里数量落在 [min,max) 的那档；多档命中挑优先级高的。 */
    private THeriDelGrade pickGrade(BigDecimal qty) {
        List<THeriDelGrade> all = this.delGradeMapper.selectList(new QueryWrapper<THeriDelGrade>()
                .eq("status", GRADE_ON).eq("del_flag", 0)
                .orderByDesc("priority").orderByAsc("id"));
        for (THeriDelGrade g : all) {
            if (g.getQtyMin() != null && qty.compareTo(g.getQtyMin()) < 0) {
                continue;
            }
            if (g.getQtyMax() != null && qty.compareTo(g.getQtyMax()) >= 0) {
                continue;
            }
            return g;
        }
        return null;
    }

    @Override
    public DelSignReceipt sign(String billNo, Integer gateNo, String action, String signer, Date at) {
        THeriDelBill bill = selectByBillNo(billNo);
        if (bill == null) {
            throw new IllegalArgumentException("册上没有这一票: " + billNo);
        }
        String who = requireSigner(signer);
        String act = trim(action);
        final Date land = at != null ? at : new Date();
        final int asked = gateNo == null ? bill.getCurGate().intValue() : gateNo.intValue();

        synchronized (lockOf(bill.getBillNo())) {
            // 锁里重读：抢签串行后，状态以前一个落完的为准
            bill = selectByBillNo(billNo);
            final int total = bill.getGateCount().intValue();
            final int round = bill.getRoundNo().intValue();
            // cur=下一道该签的号；cur-1=刚落下、还悬着的那道；再往前都是已定的截
            final int cur = bill.getCurGate().intValue();

            // 1) 关齐即封：封口后谁再加签都不作数（10:06 多的那道）
            if (bill.getStatus().intValue() == BILL_SEALED) {
                if (bill.getSealedTime() != null && land.compareTo(bill.getSealedTime()) <= 0) {
                    // 跟封口同一刻抢的：并发错开，先落的封住，后到的抢输，不是“事后加签”
                    return finishVoid(bill, asked, act, who, land,
                            THeriDelGate.ST_LOST_RACE,
                            "同一刻抢签，先落的那笔已封，后到的这笔作废");
                }
                return finishVoid(bill, asked, act, who, land,
                        THeriDelGate.ST_AFTER_SEAL,
                        "已于 " + fmt(bill.getSealedTime())
                                + " 关齐封口；册上 " + total + " 道，此后不许再加签，"
                                + (asked > total
                                        ? "第 " + asked + " 道是多出来的，"
                                        : "")
                                + "这笔不作数，以册上为准");
            }

            // 2) 数与次对不上：关次手填出格，以册上为准
            if (asked < 1 || asked > total) {
                return finishVoid(bill, asked, act, who, land,
                        THeriDelGate.ST_GATE_MISMATCH,
                        "关 " + asked + "/" + total + " 数与次对不上，以册上为准，这笔不作数");
            }

            // 3) 一道挨一道走：不许跨着走（下一道该签 cur，往后跳都是跨步）
            if (asked > cur) {
                return finishVoid(bill, asked, act, who, land,
                        THeriDelGate.ST_SKIP,
                        "第 " + cur + " 道还没落下，不许跨到第 " + asked + " 道，这笔不作数");
            }

            // 4) 落笔。先找半成品（签认落了、留痕没落成）：重办只补没写的那步
            THeriDelGate half = findHalfRow(bill, round, asked);
            if (half != null) {
                return completeHalf(bill, half, act, who, land);
            }

            THeriDelGate hanging = validGateOf(bill, round, asked);
            if (hanging != null) {
                // 这道已有一笔：已定的截不许回签——后一道落过笔，这道就定死了
                // （哪怕后一道后来被回退摆回，已定的也不跟着回来）
                if (asked < cur - 1 || gateEverLanded(bill, round, asked + 1)) {
                    return finishVoid(bill, asked, act, who, land,
                            THeriDelGate.ST_GATE_MISMATCH,
                            "第 " + asked + " 道早已定下，已定的截不许回退当没发生，以册上为准");
                }
                // 悬关再来一笔（asked==cur-1，后道没落过笔）：看时刻
                if (land.compareTo(hanging.getLandTime()) == 0) {
                    // 真同时抢签：锁已错开，先落的算数，后到的作废记账
                    return voidRaceLoser(bill, asked, act, who, land);
                }
                if (land.before(hanging.getLandTime())) {
                    return finishVoid(bill, asked, act, who, land,
                            THeriDelGate.ST_GATE_MISMATCH,
                            "落笔时刻(" + fmt(land) + ")早于在册那笔("
                                    + fmt(hanging.getLandTime()) + ")，时刻对不上，以册上为准");
                }
                // 前一笔还悬着，后一个人把笔签上：后落的算数，前一笔作废留痕（09:40/09:41）
                return supersede(bill, hanging, asked, total, round, act, who, land);
            }

            // 这道还空着：只许是下一道该签的那道（含悬关被摆回后的重落）
            if (asked != cur) {
                return finishVoid(bill, asked, act, who, land,
                        THeriDelGate.ST_GATE_MISMATCH,
                        "第 " + asked + " 道不是现在该落的道（该第 " + cur + " 道），以册上为准");
            }
            // 头一笔落这道：它一落下，前一道就此定住
            return landValid(bill, null, asked, total, round, act, who, land,
                    asked > 1 ? "第 " + (asked - 1) + " 道就此定住，" : "");
        }
    }

    /** 后落笔压前笔：旧笔标作废、记被谁压的、留痕；新笔有效。 */
    private DelSignReceipt supersede(THeriDelBill bill, THeriDelGate old, int gate, int total,
                                     int round, String action, String signer, Date land) {
        THeriDelGate now = insertGate(bill.getBillNo(), round, gate, total,
                action, signer, land, THeriDelGate.ST_VALID, null, null);
        old.setStatus(THeriDelGate.ST_SUPERSEDED);
        old.setSupersededBy(now.getId());
        old.setVoidReason("同一道后落笔(" + signer + " " + fmt(land) + ")压上，前一笔作废，痕留着");
        old.setUpdateBy(signer);
        old.setUpdateTime(new Date());
        this.delGateMapper.updateById(old);
        writeTrail(bill.getBillNo(), round, "压笔", signer,
                "第 " + gate + " 道 " + old.getSigner() + " " + fmt(old.getLandTime())
                        + " 那笔还悬着，被 " + signer + " " + fmt(land)
                        + " 后落压掉，前笔作废留痕，后笔算数");
        now.setTrailOk(1);
        this.delGateMapper.updateById(now);
        return receipt(bill, gate, now, true, "VALID",
                "压掉第 " + gate + " 道悬着的前一笔(" + old.getSigner() + ")，这笔算数", false);
    }

    /** 抢签后到：后到的作废记账，先落的不动。 */
    private DelSignReceipt voidRaceLoser(THeriDelBill bill, int gate, String action,
                                         String signer, Date land) {
        return finishVoid(bill, gate, action, signer, land, THeriDelGate.ST_LOST_RACE,
                "同一刻两人抢签第 " + gate + " 道，已并发错开：先落的算数，这笔后到作废");
    }

    /** 落下一笔有效签 + 同次留痕；末道落下即关齐封口。 */
    private DelSignReceipt landValid(THeriDelBill bill, THeriDelGate reuse, int gate, int total,
                                     int round, String action, String signer, Date land,
                                     String prefix) {
        THeriDelGate g;
        boolean halfDone = reuse != null;
        if (halfDone) {
            // 重办只补没写的：签认原笔（谁落的、何时落的）一个字不动
            g = reuse;
        } else {
            g = insertGate(bill.getBillNo(), round, gate, total,
                    action, signer, land, THeriDelGate.ST_VALID, null, null);
            // 先落签认（trail_ok 还挂 0），再同次落留痕
            writeTrail(bill.getBillNo(), round, action, signer,
                    prefix + "第 " + gate + "/" + total + " 道落签");
        }
        g.setTrailOk(1);
        this.delGateMapper.updateById(g);

        boolean sealed = gate == total;
        // cur=下一道该签的号：落下第 gate 道，游标推到 gate+1；末道封口径直停在末道
        bill.setCurGate(sealed ? gate : gate + 1);
        if (sealed) {
            bill.setStatus(BILL_SEALED);
            // 封口以落笔那一刻为准（半成品补痕不改原落笔时刻）
            bill.setSealedTime(halfDone ? g.getLandTime() : land);
            bill.setFinalRoundNo(round);
            writeTrail(bill.getBillNo(), round, "封口", signer,
                    "第 " + total + " 道落定，关齐即封；以第 " + round
                            + " 遍为准，此后不加签。最后定住的是第 " + gate + " 道("
                            + g.getSigner() + " " + fmt(g.getLandTime()) + ")");
        }
        bill.setUpdateBy(signer);
        bill.setUpdateTime(new Date());
        this.delBillMapper.updateById(bill);
        return receipt(bill, gate, g, true, "VALID",
                sealed ? "末道落定，关齐封口" : "第 " + gate + " 道落笔算数", sealed);
    }

    /** 不作数的笔也照记：流水留一笔作废痕 + 留痕一处，两处同次落。 */
    private DelSignReceipt finishVoid(THeriDelBill bill, int gate, String action, String signer,
                                      Date land, int status, String reason) {
        THeriDelGate g = insertGate(bill.getBillNo(), bill.getRoundNo().intValue(), gate,
                bill.getGateCount().intValue(), action, signer, land, status, reason, null);
        writeTrail(bill.getBillNo(), bill.getRoundNo().intValue(), "作废", signer,
                "第 " + gate + " 道落笔不作数(" + statusText(status) + ")：" + reason);
        g.setTrailOk(1);
        this.delGateMapper.updateById(g);
        // 作废笔不封任何口；册子封没封查册子头，不混在这笔的回执里
        DelSignReceipt rc = receipt(bill, gate, g, false, outcomeOf(status), reason, false);
        rc.setRoundNo(bill.getRoundNo());
        return rc;
    }

    @Override
    public THeriDelBill rollback(String billNo, String operator) {
        THeriDelBill bill = selectByBillNo(billNo);
        if (bill == null) {
            throw new IllegalArgumentException("册上没有这一票: " + billNo);
        }
        synchronized (lockOf(bill.getBillNo())) {
            bill = selectByBillNo(billNo);
            if (bill.getStatus().intValue() == BILL_SEALED) {
                throw new IllegalStateException(
                        "已关齐封口，不能回退；要动先整件收口重来: " + billNo);
            }
            int round = bill.getRoundNo().intValue();
            int cur = bill.getCurGate().intValue();
            // 悬着没定的是刚落下的那道：cur-1
            int hangGate = cur - 1;
            THeriDelGate hanging = hangGate >= 1 ? validGateOf(bill, round, hangGate) : null;
            if (hanging == null) {
                // 一道都还没落：没有悬着没定的段，无笔可摆
                return bill;
            }
            // 只摆还没定下来的这一段：悬笔摆回；已定的 1..hangGate-1 一道不动
            hanging.setStatus(THeriDelGate.ST_ROLLED_BACK);
            hanging.setVoidReason(
                    "回退只摆悬着没定的段：第 " + hangGate + " 道这笔摆回，笔痕留着");
            hanging.setUpdateBy(operator);
            hanging.setUpdateTime(new Date());
            this.delGateMapper.updateById(hanging);
            bill.setRollbackCnt(bill.getRollbackCnt().intValue() + 1);
            // 游标摆回这道等重落；已定的前几道不跟着回来
            bill.setCurGate(hangGate);
            bill.setUpdateBy(operator);
            bill.setUpdateTime(new Date());
            this.delBillMapper.updateById(bill);
            writeTrail(bill.getBillNo(), round, "回退", operator,
                    "第 " + hangGate + " 道悬着的那笔(" + hanging.getSigner() + ")摆回；"
                            + "已定的前 " + (hangGate - 1) + " 道原地不动；累计退 "
                            + bill.getRollbackCnt() + " 回");
            return bill;
        }
    }

    @Override
    public THeriDelBill reopen(String billNo, String operator) {
        THeriDelBill bill = selectByBillNo(billNo);
        if (bill == null) {
            throw new IllegalArgumentException("册上没有这一票: " + billNo);
        }
        synchronized (lockOf(bill.getBillNo())) {
            bill = selectByBillNo(billNo);
            if (bill.getStatus().intValue() != BILL_SEALED) {
                throw new IllegalStateException(
                        "还在办、没封口，用不着收口重来: " + billNo);
            }
            int oldRound = bill.getRoundNo().intValue();
            int newRound = oldRound + 1;
            // 旧遍一刀不动：流水全留，只在头上另起一遍、从第一道重走
            bill.setRoundNo(newRound);
            bill.setStatus(BILL_RUNNING);
            bill.setCurGate(1);
            bill.setSealedTime(null);
            // gate_count 维持立册誊死的数；final_round_no 先留旧遍，新遍封口才改
            bill.setUpdateBy(operator);
            bill.setUpdateTime(new Date());
            this.delBillMapper.updateById(bill);
            writeTrail(bill.getBillNo(), newRound, "收口重来", operator,
                    "第 " + oldRound + " 遍已封口定住、旧账一条不动；整件收口，另起第 "
                            + newRound + " 遍从第 1 道重走，仍走册上 "
                            + bill.getGateCount() + " 道");
            return bill;
        }
    }

    @Override
    public List<THeriDelGate> listGates(String billNo) {
        if (billNo == null) {
            return new ArrayList<THeriDelGate>();
        }
        return this.delGateMapper.selectList(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", billNo).orderByAsc("round_no")
                .orderByAsc("gate_no").orderByAsc("land_seq"));
    }

    @Override
    public List<THeriDelGate> listGates(String billNo, int roundNo) {
        if (billNo == null) {
            return new ArrayList<THeriDelGate>();
        }
        return this.delGateMapper.selectList(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", billNo).eq("round_no", roundNo)
                .orderByAsc("gate_no").orderByAsc("land_seq"));
    }

    @Override
    public DelAuditResult auditBackward(String billNo) {
        THeriDelBill bill = selectByBillNo(billNo);
        DelAuditResult r = new DelAuditResult();
        if (bill == null) {
            r.getProblems().add("册上没有这一票: " + billNo);
            r.setPass(false);
            return r;
        }
        // 核最后定住的那一遍；没封过就核当前在办的遍
        int round = bill.getFinalRoundNo() != null
                ? bill.getFinalRoundNo().intValue() : bill.getRoundNo().intValue();
        int total = bill.getGateCount().intValue();
        boolean sealed = bill.getStatus().intValue() == BILL_SEALED
                && bill.getFinalRoundNo() != null
                && bill.getFinalRoundNo().intValue() == round;

        r.setBillNo(billNo);
        r.setRoundNo(round);
        r.setGateCount(total);
        r.setSealed(sealed);

        List<THeriDelGate> rows = listGates(billNo, round);
        // 先逐笔看作废痕：压笔/抢签后到/封口后加签/跨步/关次对不上——
        // 这些核得出“就是作废”，留痕在、不连累正链；NB07 那票就带着这两种痕算数。
        for (THeriDelGate row : rows) {
            int st = row.getStatus().intValue();
            if (st == THeriDelGate.ST_VALID || st == THeriDelGate.ST_ROLLED_BACK) {
                continue;
            }
            r.getVoidedTraces().add("第 " + row.getGateNo() + " 道 " + row.getSigner()
                    + " " + fmt(row.getLandTime()) + " 的笔："
                    + statusText(st) + "，作废留痕");
        }
        // 再倒着核：末道往头道，一道只认一笔有效、且签认留痕同次对得上
        for (int g = total; g >= 1; g--) {
            THeriDelGate valid = null;
            for (THeriDelGate row : rows) {
                if (row.getGateNo().intValue() == g
                        && row.getStatus().intValue() == THeriDelGate.ST_VALID) {
                    valid = row; // 一道至多一笔有效
                }
            }
            if (valid == null) {
                r.getProblems().add("第 " + g + " 道倒着核不出有效落笔，这道空着，核不出不作数");
                continue;
            }
            // 签认和留痕要同次对得上：只落一处（trail_ok=0）的核不出
            if (valid.getTrailOk() == null || valid.getTrailOk().intValue() != 1
                    || !trailExists(billNo, round, valid)) {
                r.getProblems().add("第 " + g + " 道(" + valid.getSigner()
                        + ")只落了一处、签认与留痕对不上，核不出不作数");
                continue;
            }
            r.getVerifiedGates().add(g);
        }
        boolean allGates = r.getVerifiedGates().size() == total;
        r.setPass(sealed && allGates && r.getProblems().isEmpty());
        return r;
    }

    // ---------------- 内部零碎 ----------------

    private THeriDelGate insertGate(String billNo, int round, int gate, int total,
                                    String action, String signer, Date land,
                                    int status, String reason, Long supersededBy) {
        THeriDelGate g = new THeriDelGate();
        g.setBillNo(billNo);
        g.setRoundNo(round);
        g.setGateNo(gate);
        g.setGateTotal(total);
        g.setSigner(signer);
        g.setSignAction(action);
        g.setLandTime(land);
        g.setLandSeq(this.landSeq.incrementAndGet());
        g.setStatus(status);
        g.setTrailOk(0);
        g.setVoidReason(reason);
        g.setSupersededBy(supersededBy);
        g.setDelFlag(0);
        g.setCreateBy(signer);
        g.setCreateTime(new Date());
        this.delGateMapper.insert(g);
        return g;
    }

    /** 半成品：签认在、留痕没同次落齐（trail_ok=0）；有效/作废都可能。 */
    private THeriDelGate findHalfRow(THeriDelBill bill, int round, int gate) {
        List<THeriDelGate> rows = this.delGateMapper.selectList(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", bill.getBillNo()).eq("round_no", round).eq("gate_no", gate)
                .eq("trail_ok", 0).orderByAsc("land_seq"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private THeriDelGate validGateOf(THeriDelBill bill, int round, int gate) {
        List<THeriDelGate> rows = this.delGateMapper.selectList(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", bill.getBillNo()).eq("round_no", round).eq("gate_no", gate)
                .eq("status", THeriDelGate.ST_VALID).orderByDesc("land_seq"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 这道当年是否真正落下过笔（有效/被后落压掉/后来摆回都算；抢输、跨步等空笔不算）。 */
    private boolean gateEverLanded(THeriDelBill bill, int round, int gate) {
        if (gate < 1 || gate > bill.getGateCount().intValue()) {
            return false;
        }
        Integer cnt = this.delGateMapper.selectCount(new QueryWrapper<THeriDelGate>()
                .eq("bill_no", bill.getBillNo()).eq("round_no", round).eq("gate_no", gate)
                .in("status", THeriDelGate.ST_VALID,
                        THeriDelGate.ST_SUPERSEDED, THeriDelGate.ST_ROLLED_BACK));
        return cnt != null && cnt.intValue() > 0;
    }

    /** 重办只补没写的：半成品原笔不动，只补留痕，再按正常落笔推进/封口。 */
    private DelSignReceipt completeHalf(THeriDelBill bill, THeriDelGate half,
                                        String action, String signer, Date land) {
        int gate = half.getGateNo().intValue();
        int total = bill.getGateCount().intValue();
        int round = half.getRoundNo().intValue();
        // 补落时若这道已经被别的有效笔定住，半成品只补痕、不再算数
        THeriDelGate valid = validGateOf(bill, round, gate);
        writeTrail(bill.getBillNo(), round, "补痕", signer,
                "第 " + gate + " 道上回只落了签认、留痕没落成；重办只补没写的这步");
        if (valid != null && !valid.getId().equals(half.getId())) {
            // 这道已有定住的笔：半成品只补痕，本身不许再挂有效（一道只容一笔有效）
            half.setStatus(THeriDelGate.ST_SUPERSEDED);
            half.setVoidReason("这道已有定住的笔(" + valid.getSigner()
                    + ")，半成品补齐留痕后不另算数");
            half.setTrailOk(1);
            half.setUpdateBy(signer);
            half.setUpdateTime(new Date());
            this.delGateMapper.updateById(half);
            return receipt(bill, gate, valid, false, "SUPERSEDED",
                    "这道已有定住的笔，半成品只补齐留痕、不作数", false);
        }
        // 半成品本身不是有效笔（作废的那笔上次留痕没落）：只补齐留痕，不另推进
        if (half.getStatus().intValue() != THeriDelGate.ST_VALID) {
            half.setTrailOk(1);
            half.setUpdateBy(signer);
            half.setUpdateTime(new Date());
            this.delGateMapper.updateById(half);
            DelSignReceipt rc = receipt(bill, gate, half, false, outcomeOf(half.getStatus()),
                    half.getVoidReason(), false);
            rc.setRoundNo(round);
            return rc;
        }
        // 有效半成品：签认原笔不动，补完痕后照常把游标推到下一道（末道则封口）
        return landValid(bill, half, gate, total, round,
                action == null ? half.getSignAction() : action, signer, land, "");
    }

    private boolean trailExists(String billNo, int round, THeriDelGate g) {
        Integer cnt = this.sysOperLogMapper.selectCount(new QueryWrapper<TSysOperLog>()
                .eq("oper_url", trailKey(billNo)).eq("create_by", g.getSigner()));
        return cnt != null && cnt.intValue() > 0;
    }

    /** 留痕缝：单测可覆写它捅故障（只落签认、留痕失败那类）。 */
    protected void writeTrail(String billNo, int round, String action, String signer, String param) {
        TSysOperLog log = new TSysOperLog();
        log.setTitle("放货台");
        log.setMethod(action);
        log.setOperName(signer);
        log.setOperUrl(trailKey(billNo));
        log.setOperParam("第 " + round + " 遍｜" + param);
        log.setOperTime(new Date());
        log.setCreateBy(signer);
        log.setCreateTime(new Date());
        this.sysOperLogMapper.insert(log);
    }

    private Object lockOf(String billNo) {
        Object exist = this.billLocks.get(billNo);
        if (exist != null) {
            return exist;
        }
        Object fresh = new Object();
        Object old = this.billLocks.putIfAbsent(billNo, fresh);
        return old == null ? fresh : old;
    }

    private DelSignReceipt receipt(THeriDelBill bill, int gate, THeriDelGate g,
                                   boolean counted, String outcome, String reason,
                                   boolean sealed) {
        DelSignReceipt rc = new DelSignReceipt();
        rc.setBillNo(bill.getBillNo());
        rc.setGateNo(gate);
        rc.setLandSeq(g.getLandSeq());
        rc.setSigner(g.getSigner());
        rc.setAction(g.getSignAction());
        rc.setRoundNo(bill.getRoundNo());
        rc.setCounted(counted);
        rc.setOutcome(outcome);
        rc.setReason(reason);
        // sealed 严格表示“这笔把口封了”：不作数的笔（抢输/封口后加签）不报 sealed
        rc.setSealed(sealed);
        return rc;
    }

    private static String outcomeOf(int status) {
        switch (status) {
            case THeriDelGate.ST_SUPERSEDED:
                return "SUPERSEDED";
            case THeriDelGate.ST_LOST_RACE:
                return "LOST_RACE";
            case THeriDelGate.ST_AFTER_SEAL:
                return "AFTER_SEAL";
            case THeriDelGate.ST_GATE_MISMATCH:
                return "GATE_MISMATCH";
            case THeriDelGate.ST_SKIP:
                return "SKIP";
            case THeriDelGate.ST_ROLLED_BACK:
                return "ROLLED_BACK";
            default:
                return "VALID";
        }
    }

    private static String statusText(int status) {
        switch (status) {
            case THeriDelGate.ST_SUPERSEDED:
                return "后落笔压前笔";
            case THeriDelGate.ST_LOST_RACE:
                return "抢签后到";
            case THeriDelGate.ST_AFTER_SEAL:
                return "封口后加签";
            case THeriDelGate.ST_GATE_MISMATCH:
                return "关次对不上";
            case THeriDelGate.ST_SKIP:
                return "跨步签";
            case THeriDelGate.ST_ROLLED_BACK:
                return "回退摆回";
            default:
                return "有效";
        }
    }

    private static String requireSigner(String signer) {
        String s = trim(signer);
        if (s == null) {
            throw new IllegalArgumentException("落签人空着，这笔没法落");
        }
        return s;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String fmt(Date d) {
        return d == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private static String trailKey(String billNo) {
        return "/heri/release/" + billNo;
    }
}
