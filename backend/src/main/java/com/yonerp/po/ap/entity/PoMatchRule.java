package com.yonerp.po.ap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 匹配规则配置
 */
@Data
@TableName("po_match_rule")
public class PoMatchRule {

    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 适用范围：GLOBAL/SUPPLIER/MATERIAL_CATEGORY/CURRENCY */
    private String scopeType;

    /** 适用范围值（供应商ID/物料分类ID/币种） */
    private String scopeValue;

    /** 数量容差 */
    private BigDecimal qtyTolerance;

    /** 数量容差单位：PERCENT/ABSOLUTE */
    private String qtyToleranceUnit;

    /** 金额容差 */
    private BigDecimal amountTolerance;

    /** 金额容差单位：PERCENT/ABSOLUTE */
    private String amountToleranceUnit;

    /** 是否自动结算：0否/1是 */
    private Integer autoSettle;

    /** 暂估回冲方式：SINGLE(单到)/MONTHLY(月初) */
    private String accrualMode;

    /** 是否允许强制匹配 */
    private Integer allowForceMatch;

    /** 强制匹配审批角色 */
    private String approvalRole;

    /** 启用状态 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
