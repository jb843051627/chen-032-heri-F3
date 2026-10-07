package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 逐行倒查结果：一行落在哪一档，跟三笔加出来的数对不对得上。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "CusRowTrace", description = "逐行倒查")
public class CusRowTrace implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "册号")
    private String bookNo;

    @ApiModelProperty(value = "行次(册上排，终身不改)")
    private Integer rowNo;

    @ApiModelProperty(value = "报关单号")
    private String entryId;

    @ApiModelProperty(value = "档位 0待办 1已签 2退回")
    private Integer status;

    @ApiModelProperty(value = "档位名")
    private String statusName;

    @ApiModelProperty(value = "进门趟次")
    private Integer intakeSeq;

    @ApiModelProperty(value = "退回原因(退回档时有)")
    private String backReason;

    /** 这一行计入三笔里的哪一笔：进门(in)/签下(signed)/退回(back) */
    @ApiModelProperty(value = "计入哪一笔 in/signed/back")
    private String tally;

    @ApiModelProperty(value = "跟册上三笔对得上吗")
    private boolean consistent;

    /** 对不上时按行点名：哪一行、差在哪 */
    @ApiModelProperty(value = "对不上时的点名说明")
    private String mismatchNote;

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public Integer getIntakeSeq() {
        return intakeSeq;
    }

    public void setIntakeSeq(Integer intakeSeq) {
        this.intakeSeq = intakeSeq;
    }

    public String getBackReason() {
        return backReason;
    }

    public void setBackReason(String backReason) {
        this.backReason = backReason;
    }

    public String getTally() {
        return tally;
    }

    public void setTally(String tally) {
        this.tally = tally;
    }

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    public String getMismatchNote() {
        return mismatchNote;
    }

    public void setMismatchNote(String mismatchNote) {
        this.mismatchNote = mismatchNote;
    }
}
