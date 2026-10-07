package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 解析那一趟拦在门外的行：不落册、不给行次，只在回执上点得出它。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "CusDoorReject", description = "门外拦回行")
public class CusDoorReject implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 在交单整串里的次序（1 起，照着货代丢进来的次序点名） */
    @ApiModelProperty(value = "交单整串中的次序")
    private Integer sheetLine;

    @ApiModelProperty(value = "报关单号")
    private String entryId;

    @ApiModelProperty(value = "箱型(原文)")
    private String deviceKind;

    @ApiModelProperty(value = "拦回原因")
    private String reason;

    public CusDoorReject() {
    }

    public CusDoorReject(Integer sheetLine, String entryId, String deviceKind, String reason) {
        this.sheetLine = sheetLine;
        this.entryId = entryId;
        this.deviceKind = deviceKind;
        this.reason = reason;
    }

    public Integer getSheetLine() {
        return sheetLine;
    }

    public void setSheetLine(Integer sheetLine) {
        this.sheetLine = sheetLine;
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public String getDeviceKind() {
        return deviceKind;
    }

    public void setDeviceKind(String deviceKind) {
        this.deviceKind = deviceKind;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
