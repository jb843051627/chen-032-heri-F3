package com.fc.v2.service;

import com.fc.v2.model.auto.THeriCusBook;
import com.fc.v2.model.auto.THeriCusRow;
import com.fc.v2.model.custom.CusIntakeCmd;
import com.fc.v2.model.custom.CusIntakeReceipt;
import com.fc.v2.model.custom.CusRowTrace;

import java.util.List;

/**
 * 报关核放册子 Service接口（whole-string intake 形状：整串进门，无散行增删改）。
 *
 * <p>规矩定死：一串一串地办；同批第二回只照面、不动头一回的账；
 * 申报数与实签数缺一不办、不等就退（逐行写差异）；行序由册上排；
 * 箱型不合拦在门外不落册；末尾三笔签+退=进门，同趟取数不另核。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITHeriCusBookService {

    /** 按册号查头 */
    THeriCusBook selectByBookNo(String bookNo);

    /** 按串业务键+来路查头（船代、平台各录各的，同键不同来路是两串） */
    THeriCusBook selectByBiz(String bizNo, String source);

    /**
     * 整串进门。只收整串：解析只跑一趟，箱型逐行先核，不合的当场拦在门外不落册；
     * 门内的行逐行定去向（签下／退回），三笔同趟取数。同串再进只照面，
     * 续办只补退回行、行号终身不改，已签行不回待签、不另落。空串回“空册”、不出收讫。
     */
    CusIntakeReceipt intake(CusIntakeCmd cmd, String operator);

    /** 册子行（行次升序，存量行一条不减） */
    List<THeriCusRow> listRows(String bookNo);

    /** 按册号+行次取单行 */
    THeriCusRow findRow(String bookNo, Integer rowNo);

    /** 倒查一行：落在哪一档、计入三笔哪一笔、跟册上三笔对不对得上（对不上按行点名） */
    CusRowTrace traceRow(String bookNo, Integer rowNo);

    /**
     * 以“逐行定去向”重算三笔，跟头表对账。空列表=对得上；
     * 对不上时每条按行点名（第N行/报关单号/档位），不许含糊。
     */
    List<String> audit(String bookNo);
}
