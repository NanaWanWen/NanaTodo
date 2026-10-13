package com.yonerp.po.agent.service;

import com.yonerp.po.agent.dto.AgentAlertVO;
import com.yonerp.po.agent.dto.ChatRequest;
import com.yonerp.po.agent.dto.SuggestionVO;

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
     * 当前未处理预警列表（Redis）
     */
    List<AgentAlertVO> pendingAlerts();

    /**
     * 标记预警已处理
     */
    void markAlertHandled(String alertId);
}
