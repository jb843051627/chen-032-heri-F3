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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 报关核放册子行对象 t_heri_cus_row
 *
 * <p>行次（row_no）由册上在本串内自己排，没有手输的地方；一行退了隔天再报，
 * 认的还是当初那个号——行落定后 row_no 永不再改。状态只许顺着走：
 * 待办→已签/退回，已签是终态，任何重复进门都不能把它冲回待签。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_cus_row")
@ApiModel(value = "THeriCusRow", description = "报关核放册子行")
public class THeriCusRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 册号 */
    @TableField("book_no")
    @ApiModelProperty(value = "册号")
    private String bookNo;

    /** 行次：册上在本串内排 1..N，落定即终身，退了再报还是这个号 */
    @TableField("row_no")
    @ApiModelProperty(value = "行次(册上排)")
    private Integer rowNo;

    /** 报关单号（业务键，续报凭它认行） */
    @TableField("entry_id")
    @ApiModelProperty(value = "报关单号")
    private String entryId;

    /** 申报件数：缺了不办，不当零 */
    @TableField("declare_qty")
    @ApiModelProperty(value = "申报件数")
    private BigDecimal declareQty;

    /** 册上实签件数：缺了不办，不当零 */
    @TableField("signed_qty")
    @ApiModelProperty(value = "册上实签件数")
    private BigDecimal signedQty;

    /** 箱型：空着按“没写”核，不当“都可以”；不合的行拦在册门外 */
    @TableField("device_kind")
    @ApiModelProperty(value = "箱型")
    private String deviceKind;

    /** 船名航次 */
    @TableField("ship_name")
    @ApiModelProperty(value = "船名航次")
    private String shipName;

    /** 行状态 0待办 1已签 2退回（已签为终态，不回待办、不另落） */
    @TableField("status")
    @ApiModelProperty(value = "行状态 0待办 1已签 2退回")
    private Integer status;

    /** 进门趟次：头一趟=1，续办一趟+1；门外拦回的趟次留痕在回执的 reject 明细里，不占行 */
    @TableField("intake_seq")
    @ApiModelProperty(value = "进门趟次")
    private Integer intakeSeq;

    /** 退回原因/差异（逐行写得出，如“少 2 件”“多 1 件”“箱型不合 X”） */
    @TableField("back_reason")
    @ApiModelProperty(value = "退回原因")
    private String backReason;

    /** 删除标记 0正常 1删除 */
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

    public BigDecimal getDeclareQty() {
        return declareQty;
    }

    public void setDeclareQty(BigDecimal declareQty) {
        this.declareQty = declareQty;
    }

    public BigDecimal getSignedQty() {
        return signedQty;
    }

    public void setSignedQty(BigDecimal signedQty) {
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
