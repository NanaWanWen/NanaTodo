package com.yonerp.po.agent.rule;

import com.yonerp.po.agent.entity.AgentAlertRule;

import java.util.List;

/**
 * 预警规则评估器 SPI
 * <p>
 * 每个实现负责一类规则的判定逻辑（采购/库存/财务），
 * 由 {@code AlertRuleService} 按 {@link #supports(String)} 路由分发。
 * 新增规则只需实现本接口并注册为 Spring Bean，无需改动扫描任务。
 * </p>
 */
public interface AlertRuleEvaluator {

    /**
     * 该评估器支持的规则编码
     */
    String ruleCode();

    /**
     * 是否支持指定规则编码
     */
    default boolean supports(String code) {
        return ruleCode().equals(code);
    }

    /**
     * 业务域：PURCHASE / INVENTORY / FINANCE
     */
    String bizDomain();

    /**
     * 预警类型（写入 agent_alert.alert_type）
     */
    String alertType();

    /**
     * 默认规则名称（用于初始化内置规则）
     */
    String defaultRuleName();

    /**
     * 执行评估，返回命中的预警候选
     *
     * @param rule 规则配置（含阈值/级别/扩展参数）
     */
    List<AlertCandidate> evaluate(AgentAlertRule rule);
}
