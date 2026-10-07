package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 放货开册命令：单号、数量交进来，归哪一档、走几道关全照港里判据定。
 * 判据没写到的数量，服务端退回重定（开册即抛），系统不自己挑。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "DelOpenCmd", description = "放货开册命令")
public class DelOpenCmd implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "放货单号(册号，空着册上发号)")
    private String billNo;

    @ApiModelProperty(value = "数量(分档只看它)")
    private BigDecimal qty;

    @ApiModelProperty(value = "船名航次(只记不判)")
    private String shipName;

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }
}
