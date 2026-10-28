package com.yonerp.po.agent.service;

import com.yonerp.po.agent.dto.AgentAlertVO;
import com.yonerp.po.agent.dto.AlertActionVO;
import com.yonerp.po.agent.dto.ChatRequest;
import com.yonerp.po.agent.dto.SuggestionVO;
import com.yonerp.po.agent.entity.AgentAlertRule;

import java.util.List;

/**
 * AI Agent 服务
 */
public interface AgentService {

    /**
     * 流式对话（SSE token 回调）
     *
     * @param request    聊天请求
     * @param onToken    每收到一个 token 回调
     * @param onDone     结束回调（携带完整回复）
     * @param onError    异常回调
     */
    void chatStream(ChatRequest request,
                    java.util.function.Consumer<String> onToken,
                    java.util.function.BiConsumer<String, Throwable> onDone,
                    java.util.function.Consumer<Throwable> onError);

    /**
     * 同步对话（非流式，超时 60s）
     */
    String chat(ChatRequest request);

    /**
     * 生成预警（用于 AlertScanJob）
     *
     * @param alertType 预警类型
     * @param refId     关联单据 ID
     * @param refNo     关联单据编号
     * @param detail    预警详情（供 LLM 生成文案）
     * @return AI 生成的预警标题与正文
     */
    AgentAlertVO generateAlert(String alertType, Long refId, String refNo, String detail);

    /**
     * 辅助审批意见
     */
    SuggestionVO suggestApproval(Long orderId, String orderContext);

    /**
     * 发票校验建议
     */
    SuggestionVO suggestInvoiceCheck(Long invoiceId, String invoiceContext);

    /**
     * 付款建议
     */
    SuggestionVO suggestPayment(Long invoiceId, String invoiceContext);

    /**
     * 预警列表（持久化，默认只返回未处理且未忽略的预警）
     *
     * @param handled 是否查询已处理预警（null 表示未处理）
     * @param ignored 是否查询已忽略预警（null 表示未忽略）
     */
    List<AgentAlertVO> listAlerts(Boolean handled, Boolean ignored);

    /**
     * 当前未处理且未忽略的预警列表
     */
    List<AgentAlertVO> pendingAlerts();

    /**
     * 标记预警已处理
     */
    void markAlertHandled(String alertId);

    /**
     * 忽略预警（不再显示，但保留历史记录）
     */
    void ignoreAlert(String alertId);

    /**
     * 恢复已忽略的预警
     */
    void restoreAlert(String alertId);

    // ==================== 新增方法 ====================

    /**
     * 标记预警已读
     */
    void markAlertRead(String alertId);

    /**
     * 指派预警给指定人
     */
    void assignAlert(String alertId, Long assigneeId, String assignee, String note);

    /**
     * 转办预警给指定人
     */
    void transferAlert(String alertId, Long assigneeId, String assignee, String note);

    /**
     * 处理预警（标记已处理 + 处理意见）
     */
    void processAlert(String alertId, String note);

    /**
     * 预警处理流水（审计时间线）
     */
    List<AlertActionVO> getAlertActions(String alertId);

    /**
     * 站内未读预警计数
     */
    long unreadCount();

    /**
     * 规则列表
     */
    List<AgentAlertRule> listAll();

    /**
     * 创建规则
     */
    AgentAlertRule create(AgentAlertRule rule);

    /**
     * 更新规则
     */
    AgentAlertRule update(Long id, AgentAlertRule rule);

    /**
     * 删除规则
     */
    void delete(Long id);
}
