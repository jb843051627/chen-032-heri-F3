package com.fc.v2.model.custom;

import com.fc.v2.model.auto.THeriDelSign;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 关口落一笔的结果：落成了、压了前一笔、被压作废、被拒不算数、还是照面（重办）。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "DelStroke", description = "关口落笔结果")
public class DelStroke implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 落签：顺次落下一道 */
    public static final String OUT_SIGN = "落签";
    /** 压签：后一笔压前一笔，前一笔作废留痕 */
    public static final String OUT_RESIGN = "压签";
    /** 作废：抢签落败——先落的那一笔算数，后到的作废并记账 */
    public static final String OUT_VOIDED = "作废";
    /** 被拒：这趟不算数（多了一道以册上为准/已封口），痕留着 */
    public static final String OUT_REJECTED = "被拒";
    /** 回退：悬着的那一道被退下，留痕 */
    public static final String OUT_ROLLBACK = "回退";
    /** 照面：同一笔重办，册上已有，原样返回不重复落 */
    public static final String OUT_FACE_ONLY = "照面";

    @ApiModelProperty(value = "落笔结果(落签/压签/作废/被拒/回退/照面)")
    private String outcome;

    @ApiModelProperty(value = "落在册上的那一笔")
    private THeriDelSign stroke;

    @ApiModelProperty(value = "人话说明")
    private String note;

    public DelStroke() {
    }

    public DelStroke(String outcome, THeriDelSign stroke, String note) {
        this.outcome = outcome;
        this.stroke = stroke;
        this.note = note;
    }

    /** 这一笔算不算新落成的：只有落签、压签算；照面是重办，算不算数看册上那笔自己 */
    public boolean counts() {
        return OUT_SIGN.equals(outcome) || OUT_RESIGN.equals(outcome);
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public THeriDelSign getStroke() {
        return stroke;
    }

    public void setStroke(THeriDelSign stroke) {
        this.stroke = stroke;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
