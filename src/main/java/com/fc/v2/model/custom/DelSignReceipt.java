package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 关口落签回执：一笔回来告诉交单方它到底算不算数、落在哪一道、册子封没封。
 * 不算数的笔也要点明白缘由——页面上先亮出来的不算数，以这张回执/册上为准。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "DelSignReceipt", description = "关口落签回执")
public class DelSignReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "放货单号")
    private String billNo;

    @ApiModelProperty(value = "这一笔落在哪道(系统排的)")
    private Integer gateNo;

    @ApiModelProperty(value = "落笔全局次序")
    private Long landSeq;

    /** VALID=算数；其余皆不作数（压笔/抢签后到/封口后加签/多过道/跨步） */
    @ApiModelProperty(value = "处置结果 VALID/SUPERSEDED/LOST_RACE/AFTER_SEAL/GATE_MISMATCH/SKIP")
    private String outcome;

    @ApiModelProperty(value = "这笔算不算数")
    private boolean counted;

    @ApiModelProperty(value = "落签人")
    private String signer;

    @ApiModelProperty(value = "落签动作")
    private String action;

    @ApiModelProperty(value = "当前走到第几遍")
    private Integer roundNo;

    @ApiModelProperty(value = "落笔后册子关齐封住没有")
    private boolean sealed;

    @ApiModelProperty(value = "不作数/压笔缘由")
    private String reason;

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getGateNo() {
        return gateNo;
    }

    public void setGateNo(Integer gateNo) {
        this.gateNo = gateNo;
    }

    public Long getLandSeq() {
        return landSeq;
    }

    public void setLandSeq(Long landSeq) {
        this.landSeq = landSeq;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public boolean isCounted() {
        return counted;
    }

    public void setCounted(boolean counted) {
        this.counted = counted;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public boolean isSealed() {
        return sealed;
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
