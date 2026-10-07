package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽验报告：随机抽一单倒着核——核得出的算数，核不出的那几笔不作数。
 *
 * <p>只认册上（签认册+留痕册）两本账；页面上先亮出来的（头表汇总数）不算依据，
 * 对不上时以册上为准并点名。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "DelAudit", description = "放货台抽验报告")
public class DelAudit implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "凭证代号")
    private String billNo;

    @ApiModelProperty(value = "第几趟")
    private Integer roundNo;

    @ApiModelProperty(value = "整趟核不核得过")
    private boolean ok;

    /** 核得出的笔（算数）：签认册主键 */
    @ApiModelProperty(value = "核得出的笔(算数)")
    private List<Long> reconciled = new ArrayList<Long>();

    /** 核不出的笔（不作数）：哪一笔、差在哪 */
    @ApiModelProperty(value = "核不出的笔(不作数)")
    private List<Miss> unreconciled = new ArrayList<Miss>();

    @ApiModelProperty(value = "逐条点名说明")
    private List<String> notes = new ArrayList<String>();

    /** 核不出的一笔 */
    @ApiModel(value = "DelAudit.Miss", description = "核不出的一笔")
    public static class Miss implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "签认册主键(无对应笔时为null)")
        private Long strokeId;

        @ApiModelProperty(value = "差在哪")
        private String reason;

        public Miss() {
        }

        public Miss(Long strokeId, String reason) {
            this.strokeId = strokeId;
            this.reason = reason;
        }

        public Long getStrokeId() {
            return strokeId;
        }

        public void setStrokeId(Long strokeId) {
            this.strokeId = strokeId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

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

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public List<Long> getReconciled() {
        return reconciled;
    }

    public void setReconciled(List<Long> reconciled) {
        this.reconciled = reconciled;
    }

    public List<Miss> getUnreconciled() {
        return unreconciled;
    }

    public void setUnreconciled(List<Miss> unreconciled) {
        this.unreconciled = unreconciled;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }
}
