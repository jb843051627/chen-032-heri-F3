package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 进门回执：末尾三笔（进门／签下／退回）全取自同一趟“逐行定去向”，
 * 后两笔加起来恒等于头一笔；页面上抢先亮的数不算，两头对不上以这一趟返回为准。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "CusIntakeReceipt", description = "整串进门回执")
public class CusIntakeReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "册号")
    private String bookNo;

    @ApiModelProperty(value = "串业务键")
    private String bizNo;

    @ApiModelProperty(value = "来源")
    private String source;

    @ApiModelProperty(value = "进门趟次(头趟1)")
    private Integer intakeSeq;

    /** true=头一趟已在册；true=第二遍及以后只照面、续办也照已在册的来 */
    @ApiModelProperty(value = "是否只照面(未动第一遍的账)")
    private boolean faceOnly;

    @ApiModelProperty(value = "串头那格报的行数(空按150)")
    private Integer declaredRows;

    @ApiModelProperty(value = "本趟交了几行(门外那趟数)")
    private Integer submittedRows;

    /** 三笔：进门=本趟逐行定去向的行（门内核过的），签+退=进门 */
    @ApiModelProperty(value = "进门行数(本趟)")
    private Integer inRows;

    @ApiModelProperty(value = "签下行数(本趟)")
    private Integer signedRows;

    @ApiModelProperty(value = "退回行数(本趟)")
    private Integer backRows;

    /** 门外拦回：箱型不合等，不落册、不计进三笔 */
    @ApiModelProperty(value = "门外拦回明细")
    private List<CusDoorReject> doorRejects = new ArrayList<CusDoorReject>();

    /** 串头数字之外多出来的行：不许就地往下接，另开一串 */
    @ApiModelProperty(value = "溢出待另开一串的行数")
    private Integer overflowRows;

    /** 册上累计（倒查对三笔用） */
    @ApiModelProperty(value = "册上累计进门")
    private Integer bookInRows;

    @ApiModelProperty(value = "册上累计签下")
    private Integer bookSignedRows;

    @ApiModelProperty(value = "册上累计退回")
    private Integer bookBackRows;

    @ApiModelProperty(value = "是否空册")
    private boolean emptyBook;

    @ApiModelProperty(value = "收讫句(空册/照面不出)")
    private String acceptedText;

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }

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

    public Integer getIntakeSeq() {
        return intakeSeq;
    }

    public void setIntakeSeq(Integer intakeSeq) {
        this.intakeSeq = intakeSeq;
    }

    public boolean isFaceOnly() {
        return faceOnly;
    }

    public void setFaceOnly(boolean faceOnly) {
        this.faceOnly = faceOnly;
    }

    public Integer getDeclaredRows() {
        return declaredRows;
    }

    public void setDeclaredRows(Integer declaredRows) {
        this.declaredRows = declaredRows;
    }

    public Integer getSubmittedRows() {
        return submittedRows;
    }

    public void setSubmittedRows(Integer submittedRows) {
        this.submittedRows = submittedRows;
    }

    public Integer getInRows() {
        return inRows;
    }

    public void setInRows(Integer inRows) {
        this.inRows = inRows;
    }

    public Integer getSignedRows() {
        return signedRows;
    }

    public void setSignedRows(Integer signedRows) {
        this.signedRows = signedRows;
    }

    public Integer getBackRows() {
        return backRows;
    }

    public void setBackRows(Integer backRows) {
        this.backRows = backRows;
    }

    public List<CusDoorReject> getDoorRejects() {
        return doorRejects;
    }

    public void setDoorRejects(List<CusDoorReject> doorRejects) {
        this.doorRejects = doorRejects;
    }

    public Integer getOverflowRows() {
        return overflowRows;
    }

    public void setOverflowRows(Integer overflowRows) {
        this.overflowRows = overflowRows;
    }

    public Integer getBookInRows() {
        return bookInRows;
    }

    public void setBookInRows(Integer bookInRows) {
        this.bookInRows = bookInRows;
    }

    public Integer getBookSignedRows() {
        return bookSignedRows;
    }

    public void setBookSignedRows(Integer bookSignedRows) {
        this.bookSignedRows = bookSignedRows;
    }

    public Integer getBookBackRows() {
        return bookBackRows;
    }

    public void setBookBackRows(Integer bookBackRows) {
        this.bookBackRows = bookBackRows;
    }

    public boolean isEmptyBook() {
        return emptyBook;
    }

    public void setEmptyBook(boolean emptyBook) {
        this.emptyBook = emptyBook;
    }

    public String getAcceptedText() {
        return acceptedText;
    }

    public void setAcceptedText(String acceptedText) {
        this.acceptedText = acceptedText;
    }
}
