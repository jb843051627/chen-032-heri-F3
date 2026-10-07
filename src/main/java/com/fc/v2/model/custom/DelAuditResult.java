package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽验倒核结果：从末道往头道核。
 * 核得出（有效签 + 同次留痕对得上）的关计入 verifiedGates；
 * 核不出的那几笔不作数，逐笔点名进 problems；多过道、跨步、封口对不上也在里头。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "DelAuditResult", description = "放货单倒核结果")
public class DelAuditResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "放货单号")
    private String billNo;

    @ApiModelProperty(value = "核的是哪一遍(最后定住的遍)")
    private Integer roundNo;

    @ApiModelProperty(value = "册上共几道")
    private Integer gateCount;

    @ApiModelProperty(value = "核的时候这一遍封没封口")
    private boolean sealed;

    @ApiModelProperty(value = "倒序核得出的关次(末道在前)")
    private List<Integer> verifiedGates = new ArrayList<Integer>();

    @ApiModelProperty(value = "作废笔痕(压笔/抢签后到/封口后加签/跨步/关次不符)——核得出是作废，留着不连累正链")
    private List<String> voidedTraces = new ArrayList<String>();

    @ApiModelProperty(value = "核不出的逐笔点名(缺道/只落一处留痕/有效笔对不上账)")
    private List<String> problems = new ArrayList<String>();

    @ApiModelProperty(value = "整遍算不算数(关齐+每道核得出+封口对得上)")
    private boolean pass;

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getGateCount() {
        return gateCount;
    }

    public void setGateCount(Integer gateCount) {
        this.gateCount = gateCount;
    }

    public boolean isSealed() {
        return sealed;
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
    }

    public List<Integer> getVerifiedGates() {
        return verifiedGates;
    }

    public void setVerifiedGates(List<Integer> verifiedGates) {
        this.verifiedGates = verifiedGates;
    }

    public List<String> getVoidedTraces() {
        return voidedTraces;
    }

    public void setVoidedTraces(List<String> voidedTraces) {
        this.voidedTraces = voidedTraces;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }
}
