package com.yonerp.po.agent.rule;

import com.yonerp.po.agent.entity.AgentAlertRule;
import lombok.Data;

/**
 * 规则评估产出的预警候选（尚未持久化）
 */
@Data
public class AlertCandidate {

    /** 命中的规则 */
    private AgentAlertRule rule;

    /** 业务引用ID */
    private Long refId;

    /** 业务引用编号 */
    private String refNo;

    /** 引用单据类型 ORDER/RECEIPT/INVOICE/PAYMENT/MATERIAL */
    private String refType;

    /** 前端跳转路径 */
    private String actionPath;

    /** 预警标题 */
    private String title;

    /** 预警详情 */
    private String detail;

    /** 预警级别（可由评估器覆盖规则级别） */
    private String level;

    public static AlertCandidate of(AgentAlertRule rule, Long refId, String refNo,
                                    String refType, String actionPath, String title, String detail) {
        AlertCandidate c = new AlertCandidate();
        c.setRule(rule);
        c.setRefId(refId);
        c.setRefNo(refNo);
        c.setRefType(refType);
        c.setActionPath(actionPath);
        c.setTitle(title);
        c.setDetail(detail);
        c.setLevel(rule != null ? rule.getLevel() : "WARNING");
        return c;
    }
}
