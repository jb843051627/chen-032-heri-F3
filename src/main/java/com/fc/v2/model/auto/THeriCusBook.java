package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 报关核放册子（一串一头）对象 t_heri_cus_book
 *
 * <p>册子只认整串进门：declare_rows 记的是串头那格当初报的行数（空着按 150），
 * 隔年也查得回；in_rows/signed_rows/back_rows 三笔取自同一趟“逐行定去向”，
 * 签+退恒等于进门。册上头只建一回，第二遍同册号进门只照面、不动账。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_cus_book")
@ApiModel(value = "THeriCusBook", description = "报关核放册子")
public class THeriCusBook implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 册号（一串一个号，系统发） */
    @TableField("book_no")
    @ApiModelProperty(value = "册号")
    private String bookNo;

    /** 串的业务键（总单号/清单号）：同键同来源再进只照面；船代和平台各录各的、两串都留 */
    @TableField("biz_no")
    @ApiModelProperty(value = "串业务键(总单号/清单号)")
    private String bizNo;

    /** 来源：货代丢表的来路（船代/平台…），只记不判，两家各录各的照留两串 */
    @TableField("source")
    @ApiModelProperty(value = "来源")
    private String source;

    /** 串头那格报的行数（空着按 150），原样入册，事后查得回 */
    @TableField("declare_rows")
    @ApiModelProperty(value = "串头申报行数(空按150)")
    private Integer declareRows;

    /** 进门行数（逐行定去向那一趟，本串历次进门累计在册的行） */
    @TableField("in_rows")
    @ApiModelProperty(value = "进门行数")
    private Integer inRows;

    /** 签下行数 */
    @TableField("signed_rows")
    @ApiModelProperty(value = "签下行数")
    private Integer signedRows;

    /** 退回行数 */
    @TableField("back_rows")
    @ApiModelProperty(value = "退回行数")
    private Integer backRows;

    /** 收讫记是否出过：0没出（空册/照面/续办）1出过；页面亮的数不算依据 */
    @TableField("received_flag")
    @ApiModelProperty(value = "收讫记是否出过 0否1是")
    private Integer receivedFlag;

    /** 册子状态 0在册（行可续办）1封住（全签下） */
    @TableField("status")
    @ApiModelProperty(value = "册子状态 0在册 1封住")
    private Integer status;

    /** 删除标记 0正常 1删除（存量行一条不减） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Integer getDeclareRows() {
        return declareRows;
    }

    public void setDeclareRows(Integer declareRows) {
        this.declareRows = declareRows;
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

    public Integer getReceivedFlag() {
        return receivedFlag;
    }

    public void setReceivedFlag(Integer receivedFlag) {
        this.receivedFlag = receivedFlag;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
