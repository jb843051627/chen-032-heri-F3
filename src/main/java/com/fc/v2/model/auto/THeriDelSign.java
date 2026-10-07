package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
 * 放货签认册对象 t_heri_del_sign：关口每一笔都落这册。
 *
 * <p>有效、作废、被拒的笔都留在册上，不许悄悄抹掉；
 * 一道只容一笔有效（eff_gate 唯一，作废/被拒=NULL 不占位）。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_del_sign")
@ApiModel(value = "THeriDelSign", description = "放货签认册")
public class THeriDelSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 凭证代号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "凭证代号")
    private String billNo;

    /** 第几趟(与凭证头一致) */
    @TableField("round_no")
    @ApiModelProperty(value = "第几趟")
    private Integer roundNo;

    /** 关次(系统从上往下排，不许手填) */
    @TableField("gate_no")
    @ApiModelProperty(value = "关次")
    private Integer gateNo;

    /** 谁落的 */
    @TableField("signer")
    @ApiModelProperty(value = "谁落的")
    private String signer;

    /** 关口动作(受理/单证齐全/结关) */
    @TableField("action")
    @ApiModelProperty(value = "关口动作")
    private String action;

    /** 落笔时刻(册上记的何时) */
    @TableField("signed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落笔时刻")
    private Date signedAt;

    /** 落笔幂等号(中途出错重办，只补没写的那几步) */
    @TableField("attempt_id")
    @ApiModelProperty(value = "落笔幂等号")
    private String attemptId;

    /** 这笔算不算数 0有效 1作废 2被拒 */
    @TableField("status")
    @ApiModelProperty(value = "这笔算不算数 0有效 1作废 2被拒")
    private Integer status;

    /** 作废缘由(被后一笔压下/抢签落败/回退冲下) */
    @TableField("void_reason")
    @ApiModelProperty(value = "作废缘由")
    private String voidReason;

    /** 被拒缘由(多了一道以册上为准/已封口/老趟不动) */
    @TableField("reject_reason")
    @ApiModelProperty(value = "被拒缘由")
    private String rejectReason;

    /** 压了哪一笔(签认册主键) */
    @TableField("supersedes")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "压了哪一笔")
    private Long supersedes;

    /** 有效时=关次，否则NULL；一道只容一笔有效（作废/被拒必须真空成NULL，故更新不略null） */
    @TableField(value = "eff_gate", updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "有效时=关次，否则NULL")
    private Integer effGate;

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

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Date getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Date signedAt) {
        this.signedAt = signedAt;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Long getSupersedes() {
        return supersedes;
    }

    public void setSupersedes(Long supersedes) {
        this.supersedes = supersedes;
    }

    public Integer getEffGate() {
        return effGate;
    }

    public void setEffGate(Integer effGate) {
        this.effGate = effGate;
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
