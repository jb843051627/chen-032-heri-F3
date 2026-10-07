package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 报关行整串进门交单（只收整串，不收散行）。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "CusIntakeCmd", description = "报关整串进门交单")
public class CusIntakeCmd implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 串的业务键（总单号/清单号）：同键同来源再进只照面，续办也认它 */
    @ApiModelProperty(value = "串业务键(总单号/清单号)")
    private String bizNo;

    /** 来路：船代/平台，只记不判；两家各录各的，两串都留 */
    @ApiModelProperty(value = "来源(船代/平台)")
    private String source;

    /** 串头那格原文：写几行就只当几行，空着按 150；数字之外再多的部分另开一串 */
    @ApiModelProperty(value = "串头行数原文(空按150)")
    private String declaredRows;

    /** 全串默认船名航次（行上没写时用它） */
    @ApiModelProperty(value = "船名航次")
    private String shipName;

    /** 整串明细（整串丢进来，门外逐行核，不在行上手填行次） */
    @ApiModelProperty(value = "整串明细行")
    private List<CusRowInput> rows;

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDeclaredRows() {
        return declaredRows;
    }

    public void setDeclaredRows(String declaredRows) {
        this.declaredRows = declaredRows;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }

    public List<CusRowInput> getRows() {
        return rows;
    }

    public void setRows(List<CusRowInput> rows) {
        this.rows = rows;
    }
}
