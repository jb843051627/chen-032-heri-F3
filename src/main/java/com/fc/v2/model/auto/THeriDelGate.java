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
 * 放货关口落签流水对象 t_heri_del_gate
 *
 * <p>append-only：一笔一道痕，作废也留着，谁都不许悄悄抹掉。
 * 同一关只容一笔有效；land_seq 是落笔全局次序，同一刻抢签拿它分先后。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_del_gate")
@ApiModel(value = "THeriDelGate", description = "放货关口落签流水")
public class THeriDelGate implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 流水态：1有效 */
    public static final int ST_VALID = 1;
    /** 流水态：2作废(后落笔压前笔，09:40/09:41) */
    public static final int ST_SUPERSEDED = 2;
    /** 流水态：3作废(真同时抢签，后到的那笔) */
    public static final int ST_LOST_RACE = 3;
    /** 流水态：4作废(关齐封口后再加签，10:06) */
    public static final int ST_AFTER_SEAL = 4;
    /** 流水态：5作废(关次对不上/多过道，以册上为准) */
    public static final int ST_GATE_MISMATCH = 5;
    /** 流水态：6作废(跨步签，不许跨着走) */
    public static final int ST_SKIP = 6;
    /** 流水态：7回退摆回(笔痕还在，已不作数) */
    public static final int ST_ROLLED_BACK = 7;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 放货单号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "放货单号")
    private String billNo;

    /** 第几遍 */
    @TableField("round_no")
    @ApiModelProperty(value = "第几遍")
    private Integer roundNo;

    /** 关次(系统按册从上往下排) */
    @TableField("gate_no")
    @ApiModelProperty(value = "关次")
    private Integer gateNo;

    /** 册上共几道(落签那一刻的誊本) */
    @TableField("gate_total")
    @ApiModelProperty(value = "册上共几道")
    private Integer gateTotal;

    /** 谁落的笔 */
    @TableField("signer")
    @ApiModelProperty(value = "落签人")
    private String signer;

    /** 落签动作(受理/单证齐全/结关) */
    @TableField("sign_action")
    @ApiModelProperty(value = "落签动作")
    private String signAction;

    /** 何时落的 */
    @TableField("land_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落笔时刻")
    private Date landTime;

    /** 落笔全局次序(同时刻抢签拿它分先后) */
    @TableField("land_seq")
    @ApiModelProperty(value = "落笔全局次序")
    private Long landSeq;

    /** 1有效 2作废(后落笔压) 3作废(抢签后到) 4作废(封口后加签) 5作废(关次对不上) 6作废(跨步) 7回退摆回 */
    @TableField("status")
    @ApiModelProperty(value = "流水态")
    private Integer status;

    /** 留痕是否已同次落齐 0半成品 1齐(只落一处不算数) */
    @TableField("trail_ok")
    @ApiModelProperty(value = "留痕是否同次落齐")
    private Integer trailOk;

    /** 作废/摆回缘由 */
    @TableField("void_reason")
    @ApiModelProperty(value = "作废缘由")
    private String voidReason;

    /** 被哪一笔压掉 */
    @TableField("superseded_by")
    @ApiModelProperty(value = "被哪笔压掉")
    private Long supersededBy;

    /** 删除标记 恒0(流水只许追加) */
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

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getGateNo() {
        return gateNo;
    }

    public void setGateNo(Integer gateNo) {
        this.gateNo = gateNo;
    }

    public Integer getGateTotal() {
        return gateTotal;
    }

    public void setGateTotal(Integer gateTotal) {
        this.gateTotal = gateTotal;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getSignAction() {
        return signAction;
    }

    public void setSignAction(String signAction) {
        this.signAction = signAction;
    }

    public Date getLandTime() {
        return landTime;
    }

    public void setLandTime(Date landTime) {
        this.landTime = landTime;
    }

    public Long getLandSeq() {
        return landSeq;
    }

    public void setLandSeq(Long landSeq) {
        this.landSeq = landSeq;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getTrailOk() {
        return trailOk;
    }

    public void setTrailOk(Integer trailOk) {
        this.trailOk = trailOk;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public Long getSupersededBy() {
        return supersededBy;
    }

    public void setSupersededBy(Long supersededBy) {
        this.supersededBy = supersededBy;
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
