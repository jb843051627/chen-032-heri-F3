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
 * 放货单册（一票一册）对象 t_heri_del_bill
 *
 * <p>归哪一档、走几道关、以哪一遍为准，立册时写死在头上：
 * gate_count 是判据的誊本，cur_gate 系统从上往下排，final_round_no 标记定住的那遍。
 * 往后判据加关口，老册子一条不动、照旧查得回。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_del_bill")
@ApiModel(value = "THeriDelBill", description = "放货单册")
public class THeriDelBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 放货单号(册号，一票一号) */
    @TableField("bill_no")
    @ApiModelProperty(value = "放货单号")
    private String billNo;

    /** 分档编号(立册写死) */
    @TableField("grade_code")
    @ApiModelProperty(value = "分档编号")
    private String gradeCode;

    /** 分档名称(立册写死) */
    @TableField("grade_name")
    @ApiModelProperty(value = "分档名称")
    private String gradeName;

    /** 放法(立册写死) */
    @TableField("biz_mode")
    @ApiModelProperty(value = "放法")
    private String bizMode;

    /** 数量(分档只看它) */
    @TableField("qty")
    @ApiModelProperty(value = "数量")
    private BigDecimal qty;

    /** 册上几道关(立册誊死，不随后续判据改) */
    @TableField("gate_count")
    @ApiModelProperty(value = "册上几道关")
    private Integer gateCount;

    /** 当前走到第几遍(收口重来才递增) */
    @TableField("round_no")
    @ApiModelProperty(value = "当前第几遍")
    private Integer roundNo;

    /** 最后定住的是哪一遍(以这一遍为准) */
    @TableField("final_round_no")
    @ApiModelProperty(value = "最后定住的遍")
    private Integer finalRoundNo;

    /** 册子状态 0在办 1已封住(关齐即封) */
    @TableField("status")
    @ApiModelProperty(value = "册态 0在办 1封住")
    private Integer status;

    /** 当前关次(系统从上往下排，不许手填) */
    @TableField("cur_gate")
    @ApiModelProperty(value = "当前关次")
    private Integer curGate;

    /** 退过几回 */
    @TableField("rollback_cnt")
    @ApiModelProperty(value = "退过几回")
    private Integer rollbackCnt;

    /** 封口时刻 */
    @TableField("sealed_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "封口时刻")
    private Date sealedTime;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @TableField("remark")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getGradeCode() {
        return gradeCode;
    }

    public void setGradeCode(String gradeCode) {
        this.gradeCode = gradeCode;
    }

    public String getGradeName() {
        return gradeName;
    }

    public void setGradeName(String gradeName) {
        this.gradeName = gradeName;
    }

    public String getBizMode() {
        return bizMode;
    }

    public void setBizMode(String bizMode) {
        this.bizMode = bizMode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public Integer getGateCount() {
        return gateCount;
    }

    public void setGateCount(Integer gateCount) {
        this.gateCount = gateCount;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getFinalRoundNo() {
        return finalRoundNo;
    }

    public void setFinalRoundNo(Integer finalRoundNo) {
        this.finalRoundNo = finalRoundNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCurGate() {
        return curGate;
    }

    public void setCurGate(Integer curGate) {
        this.curGate = curGate;
    }

    public Integer getRollbackCnt() {
        return rollbackCnt;
    }

    public void setRollbackCnt(Integer rollbackCnt) {
        this.rollbackCnt = rollbackCnt;
    }

    public Date getSealedTime() {
        return sealedTime;
    }

    public void setSealedTime(Date sealedTime) {
        this.sealedTime = sealedTime;
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
