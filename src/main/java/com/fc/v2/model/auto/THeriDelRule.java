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
 * 放货凭证核发判定规则对象 t_heri_del_rule
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_heri_del_rule")
@ApiModel(value = "THeriDelRule", description = "放货凭证核发判定规则")
public class THeriDelRule implements Serializable {
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

    /** 档(直发档/换单档/改提单档)：开单时按港里判据定死 */
    @TableField("grade")
    @ApiModelProperty(value = "档(直发档/换单档/改提单档)")
    private String grade;

    /** 这一档几道关，册上写死；往后加关口不追老账 */
    @TableField("gate_total")
    @ApiModelProperty(value = "这一档几道关")
    private Integer gateTotal;

    /** 第几趟(收口重来递增，头趟1) */
    @TableField("round_no")
    @ApiModelProperty(value = "第几趟")
    private Integer roundNo;

    /** 已落成关数(0..gate_total)，下一道待签=node_no+1；关齐即封口 */
    @TableField("node_no")
    @ApiModelProperty(value = "已落成关数")
    private Integer nodeNo;

    /** 退了几回 */
    @TableField("rollback_cnt")
    @ApiModelProperty(value = "退了几回")
    private Integer rollbackCnt;

    /** 落签模式 0或签 1齐签 */
    @TableField("sign_mode")
    @ApiModelProperty(value = "落签模式 0或签 1齐签")
    private Integer signMode;

    /** 本关口应落人数 */
    @TableField("need_count")
    @ApiModelProperty(value = "本关口应落人数")
    private Integer needCount;

    /** 本关口已落人数 */
    @TableField("sign_count")
    @ApiModelProperty(value = "本关口已落人数")
    private Integer signCount;

    /** 凭证状态 0在办 1已核发(关齐封口) 2已封住(收口重来的老趟) */
    @TableField("status")
    @ApiModelProperty(value = "凭证状态 0在办 1已核发 2已封住")
    private Integer status;

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

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Integer getGateTotal() {
        return gateTotal;
    }

    public void setGateTotal(Integer gateTotal) {
        this.gateTotal = gateTotal;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getRollbackCnt() {
        return rollbackCnt;
    }

    public void setRollbackCnt(Integer rollbackCnt) {
        this.rollbackCnt = rollbackCnt;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
    }

    public Integer getNeedCount() {
        return needCount;
    }

    public void setNeedCount(Integer needCount) {
        this.needCount = needCount;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public void setSignCount(Integer signCount) {
        this.signCount = signCount;
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
