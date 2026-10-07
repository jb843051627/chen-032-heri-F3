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
 * 放货分档判据对象 t_heri_del_grade
 *
 * <p>港里的判据：数量落在 [qtyMin, qtyMax) 内归这一档，gates 写死这一档走几道关。
 * 判据没写到的数量，立册即退回重定，系统不自己挑。判据只对立册那一刻负责——
 * 几道关当场誊到单册头上，往后改判据、加关口，不追老账。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_del_grade")
@ApiModel(value = "THeriDelGrade", description = "放货分档判据")
public class THeriDelGrade implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 分档编号 */
    @TableField("grade_code")
    @ApiModelProperty(value = "分档编号")
    private String gradeCode;

    /** 分档名称(直发档/换单档/改提单档) */
    @TableField("grade_name")
    @ApiModelProperty(value = "分档名称")
    private String gradeName;

    /** 放法 直发=当场放 换单=先换单 改提=交上去核 */
    @TableField("biz_mode")
    @ApiModelProperty(value = "放法")
    private String bizMode;

    /** 数量下限(含) */
    @TableField("qty_min")
    @ApiModelProperty(value = "数量下限(含)")
    private BigDecimal qtyMin;

    /** 数量上限(不含，空=不封顶) */
    @TableField("qty_max")
    @ApiModelProperty(value = "数量上限(不含)")
    private BigDecimal qtyMax;

    /** 本档几道关(册上写死) */
    @TableField("gates")
    @ApiModelProperty(value = "本档几道关")
    private Integer gates;

    /** 优先级(大数先挑) */
    @TableField("priority")
    @ApiModelProperty(value = "优先级")
    private Integer priority;

    /** 状态 0启用 1撤下 */
    @TableField("status")
    @ApiModelProperty(value = "状态 0启用 1撤下")
    private Integer status;

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

    public BigDecimal getQtyMin() {
        return qtyMin;
    }

    public void setQtyMin(BigDecimal qtyMin) {
        this.qtyMin = qtyMin;
    }

    public BigDecimal getQtyMax() {
        return qtyMax;
    }

    public void setQtyMax(BigDecimal qtyMax) {
        this.qtyMax = qtyMax;
    }

    public Integer getGates() {
        return gates;
    }

    public void setGates(Integer gates) {
        this.gates = gates;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
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
