package com.fc.v2.service;

import com.fc.v2.model.auto.THeriDelRule;
import com.fc.v2.model.auto.THeriDelRow;
import com.fc.v2.model.auto.THeriDelSign;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.DelAudit;
import com.fc.v2.model.custom.DelStroke;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 港口放货台 Service接口（gate-ledger 形状：一道一道落签，无增删改查入口）。
 *
 * <p>规矩定死（册上怎么写，系统怎么走）：</p>
 * <ul>
 *   <li>分档看数量，归哪档港里判据定；判据没写到的退回重定，系统不自己挑；</li>
 *   <li>几道关由所在档说了算，开单时写死在凭证头上，往后加关口不追老账；</li>
 *   <li>关次系统从上往下排，不许手填；一道挨一道走，不许跨着走；</li>
 *   <li>一道只容一笔：顺序重签后一笔压前一笔；两人抢着签并发错开，
 *       先落的那一笔算数、后到的那一笔作废并记账；作废的痕必须留着，不许悄悄抹掉；</li>
 *   <li>关齐即封口，此后不许再往上加签；要加签，先把整件事收口重来（开新一趟，老趟一条不动）；</li>
 *   <li>回退只退还没定下来的那道（悬着的那道）；已定的那几段原地不动，
 *       不许因为后来退了一回就当没发生过；</li>
 *   <li>签认和留痕是两边账本，同一事务落，不能只落一处；中途出错重办，只补没写的那几步；</li>
 *   <li>留痕写全：谁落的、何时落的、退了几回、最后哪一道定的；</li>
 *   <li>抽验随机抽一单倒着核：核得出的算数，核不出的那几笔不作数；
 *       页面上先亮出来的不算数，以册上为准。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITHeriDelRuleService {

    /**
     * 开单分档：看数量，按港里判据（t_heri_bl_rule 生效窗口内、优先级高者）定档，
     * 档和几道关当场写死在凭证头上。判据没写到的（数量超界/档没配关数）退回重定，
     * 系统不自己挑；退回也留一笔痕。同号再开只照面，不另立。
     */
    THeriDelRule open(String billNo, BigDecimal qty, Date at, String operator);

    /**
     * 落签下一道：关次由系统派（当前关数+1），交单方不许手填。
     * baseStroke 是签的人当时看到的册上最后一笔（null=不核，顺序办理；0=看到册上一笔都没有）；
     * 对不上即抢着签——先落的算数，这笔作废留痕。关齐即封口。
     * attemptId 是这笔的幂等号：中途出错重办，只补没写的那几步，同一号不重复落。
     */
    DelStroke sign(String billNo, String signer, String action, Date at, String attemptId, Long baseStroke);

    /**
     * 重签悬着的那一道（后一笔压前一笔）：前一笔作废留痕，新笔有效并指回被压的那笔。
     * 只能压还没定下来的那道；已定的那几段原地不动。封口后想加签，先收口重来。
     */
    DelStroke resign(String billNo, String signer, String action, Date at, String attemptId, Long baseStroke);

    /**
     * 回退：只把悬着的那一道退下（作废留痕，退了几回记一回）；
     * 已定的那几段原地不动，也不跟着回来。封口后不许退。
     */
    DelStroke rollback(String billNo, String operator, Date at, String reason);

    /**
     * 收口重来：关齐封口的整件事收口（老趟封住，一条不动，照旧查得回），
     * 照册上写死的档和关数开新一趟。
     */
    THeriDelRule redo(String billNo, String operator, Date at);

    /** 换单/交核确认：换单档先换单、改提单档交上去核，确认留痕（带趟次）。 */
    THeriDelRule confirm(String billNo, String kind, String operator, Date at);

    /**
     * 放货：关齐核发才放。直发档当场放；换单档先换单（册上要有换单确认痕）；
     * 改提单档交上去核（册上要有交核确认痕）。凭据落 t_heri_del_row，
     * 重放只照面，不重复落。
     */
    THeriDelRow release(String billNo, String operator, Date at);

    /** 按主键回查 */
    THeriDelRule selectTHeriDelRuleById(Long id);

    /** 当前趟凭证头（页面看的；对不对得上以册上为准） */
    THeriDelRule selectCurrent(String billNo);

    /** 指定趟凭证头（老单子照旧查得回） */
    THeriDelRule selectRound(String billNo, Integer roundNo);

    /** 签认册：这一趟的全部落笔（有效、作废、被拒都在，按落笔先后排） */
    List<THeriDelSign> listStrokes(String billNo, Integer roundNo);

    /** 留痕册：这一单的全部痕（谁落的、何时落的、退了几回、最后哪一道定的） */
    List<TSysOperLog> listTrail(String billNo);

    /**
     * 抽验倒着核：从最后落定的那道往回核——关次连号、每笔签认有对应留痕、
     * 作废有因、压签互认、退了几回对得上、封口对得上、多出的关都是被拒。
     * 核得出的算数，核不出的那几笔不作数；头表汇总数只是页面先亮的，对不上以册上为准。
     */
    DelAudit audit(String billNo, Integer roundNo);

    /** 随机抽一单倒着核 */
    DelAudit spotCheck();

    /** 港里判据：档→几道关（册上记录在案的默认只有直发档=3，其余由港里补） */
    Map<String, Integer> gradeGates();

    /** 港里颁行/改判据（加关口不追老账：只影响往后开的单，老单照开单时写死的走） */
    void setGradeGates(Map<String, Integer> gates);
}
