package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 报关整串里的一行交单数据。行次不在这儿——册上自己排。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "CusRowInput", description = "报关串明细行交单")
public class CusRowInput implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 报关单号：续报凭它认行，空着的行认不住、拦在门外 */
    @ApiModelProperty(value = "报关单号")
    private String entryId;

    /** 申报件数（原文，缺了不办，不当零） */
    @ApiModelProperty(value = "申报件数")
    private String declareQty;

    /** 册上实签件数（原文，缺了不办，不当零） */
    @ApiModelProperty(value = "册上实签件数")
    private String signedQty;

    /** 箱型：空着按“没写”核，不当“都可以” */
    @ApiModelProperty(value = "箱型")
    private String deviceKind;

    /** 船名航次（不写用串上的） */
    @ApiModelProperty(value = "船名航次")
    private String shipName;

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public String getDeclareQty() {
        return declareQty;
    }

    public void setDeclareQty(String declareQty) {
        this.declareQty = declareQty;
    }

    public String getSignedQty() {
        return signedQty;
    }

    public void setSignedQty(String signedQty) {
        this.signedQty = signedQty;
    }

    public String getDeviceKind() {
        return deviceKind;
    }

    public void setDeviceKind(String deviceKind) {
        this.deviceKind = deviceKind;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }
}
