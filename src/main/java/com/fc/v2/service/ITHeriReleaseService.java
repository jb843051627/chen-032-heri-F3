package com.fc.v2.service;

import com.fc.v2.model.auto.THeriDelBill;
import com.fc.v2.model.auto.THeriDelGate;
import com.fc.v2.model.custom.DelAuditResult;
import com.fc.v2.model.custom.DelOpenCmd;
import com.fc.v2.model.custom.DelSignReceipt;

import java.util.Date;
import java.util.List;

/**
 * 港口放货台 Service接口（gate-chain 形状：一票货按档过几道关）。
 *
 * <p>册子口径：</p>
 * <ul>
 *   <li>分档看数量：直发档当场放、换单档先换单、改提单档交上去核；
 *       港里判据没写到的数量，退回重定，系统不自己挑；</li>
 *   <li>几道关由所在档说了算，立册誊死在册子头上：说三道走三道，说两道走两道；</li>
 *   <li>一道挨一道走，不许跨着走；一道只容一笔有效，悬着时后落笔压前笔，笔痕留着；</li>
 *   <li>两人真同时抢着签：并发错开，先落的算数，后到的作废并记一笔；</li>
 *   <li>末道落下即关齐封口，此后不许加签；多走的那道以册上为准、不作数；</li>
 *   <li>回退只摆还悬着没定的那一段，已定的段原地不回；要改封口件先整件收口重走一遍；</li>
 *   <li>签认与留痕同一次完成，只落一处不算数，重办只补没写的那步；</li>
 *   <li>老单子一条不动，判据加关口不追老账。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITHeriReleaseService {

    /** 按单号查册子 */
    THeriDelBill selectByBillNo(String billNo);

    /**
     * 立册：按数量查港里判据定档，档上写几道关誊死在册子头上。
     * 判据没写到（含判据撤了）→ 抛 IllegalArgumentException，退回重定。
     * 同号再立只照面，原样回册子，不动账。
     */
    THeriDelBill openBill(DelOpenCmd cmd, String operator);

    /**
     * 关口落签。gateNo 空着走系统排的下一道（cur_gate，从上往下，不许手填）。
     * 每笔都回一张回执：算不算数、落在哪道、封没封、不作数缘由。
     * 页面上先亮出来的不算，以回执和册上为准。
     */
    DelSignReceipt sign(String billNo, Integer gateNo, String action, String signer, Date at);

    /** 回退：只把当前还悬着没定的那一段摆回；已定的段原地不动。封了口的不许回退。 */
    THeriDelBill rollback(String billNo, String operator);

    /** 收口重来：封口件要加签/改单，先收口旧遍（旧账一条不动），另起一遍从头走。 */
    THeriDelBill reopen(String billNo, String operator);

    /** 落签流水（各遍全量，作废/摆回的痕都在，按遍/关/落笔次序排） */
    List<THeriDelGate> listGates(String billNo);

    /** 指定一遍的落签流水 */
    List<THeriDelGate> listGates(String billNo, int roundNo);

    /**
     * 倒着核一遍（抽验用）：从末道往头道逐关核，核得出（有效签+同次留痕对得上）的算数，
     * 核不出的那几笔不作数；多过道、跨步、封口对不上逐笔点名。
     */
    DelAuditResult auditBackward(String billNo);
}
