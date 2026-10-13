package com.yonerp.po.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonerp.po.agent.config.AgentProperties;
import com.yonerp.po.agent.dto.AgentAlertVO;
import com.yonerp.po.agent.dto.ChatRequest;
import com.yonerp.po.agent.dto.SuggestionVO;
import com.yonerp.po.agent.service.AgentService;
import com.yonerp.po.agent.tool.InventoryTool;
import com.yonerp.po.agent.tool.OrderTool;
import com.yonerp.po.agent.tool.ReportTool;
import com.yonerp.po.agent.tool.SettlementTool;
import com.yonerp.po.common.RedisKeys;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * AI Agent 服务实现
 * <p>
 * 双模式运行：
 * 1. LLM 模式：配置了 API Key（DASHSCOPE_API_KEY / ZHIPU_API_KEY）时，通过 LangChain4j 调用通义千问/智谱；
 * 2. 规则降级模式：无 API Key 或 LLM 调用失败时，自动降级为直接调用业务工具（Tool），保证 Agent 功能始终可用。
 * 用户后续配置好 API Key 重启即自动切换为 LLM 模式，无需改动代码。
 * </p>
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private final AgentProperties agentProperties;
    private final StringRedisTemplate redisTemplate;
    @org.springframework.lang.Nullable
    private final ChatLanguageModel chatLanguageModel; // 可为 null（未配置 API Key 时）
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReportTool reportTool;
    private final OrderTool orderTool;
    private final InventoryTool inventoryTool;
    private final SettlementTool settlementTool;

    public AgentServiceImpl(AgentProperties agentProperties,
                            StringRedisTemplate redisTemplate,
                            @org.springframework.lang.Nullable ChatLanguageModel chatLanguageModel,
                            ReportTool reportTool,
                            OrderTool orderTool,
                            InventoryTool inventoryTool,
                            SettlementTool settlementTool) {
        this.agentProperties = agentProperties;
        this.redisTemplate = redisTemplate;
        this.chatLanguageModel = chatLanguageModel;
        this.reportTool = reportTool;
        this.orderTool = orderTool;
        this.inventoryTool = inventoryTool;
        this.settlementTool = settlementTool;
    }

    private static final String SYSTEM_PROMPT = """
            你是采购订单管理系统的 AI 助手，负责帮采购员查询报表、生成预警、辅助审批、智能单据。
            规则：
            1. 回答简洁，先给结论再给数据；涉及金额用"元"为单位。
            2. 如果用户问"哪些订单逾期了"，说明期望到货日期已过但仍在审批中的订单。
            3. 涉及审批/付款建议时，给出建议但标注"仅供参考，请人工确认"。
            """;

    // ==================== 对话 ====================

    @Override
    public void chatStream(ChatRequest request,
                           Consumer<String> onToken,
                           BiConsumer<String, Throwable> onDone,
                           Consumer<Throwable> onError) {
        if (!agentProperties.isAvailable()) {
            // 降级模式：直接调用工具并返回 JSON
            log.info("Agent in fallback mode (no API Key): message='{}'", request.getMessage());
            String fallback = ruleBasedChat(request.getMessage());
            onToken.accept(fallback);
            onDone.accept(fallback, null);
            return;
        }

        // LLM 模式：同步调用后分块推送（简化实现，后续可升级为真正的 token 流式）
        try {
            String response = llmChat(request);
            // 模拟流式：按 10 字符分块推送
            for (int i = 0; i < response.length(); i += 10) {
                onToken.accept(response.substring(i, Math.min(i + 10, response.length())));
            }
            onDone.accept(response, null);
        } catch (Exception e) {
            log.warn("Agent LLM chat failed, falling back to rule mode: {}", e.getMessage());
            String fallback = ruleBasedChat(request.getMessage());
            onToken.accept(fallback);
            onDone.accept(fallback, e);
        }
    }

    @Override
    public String chat(ChatRequest request) {
        if (!agentProperties.isAvailable()) {
            log.info("Agent in fallback mode (no API Key): message='{}'", request.getMessage());
            return ruleBasedChat(request.getMessage());
        }
        try {
            return llmChat(request);
        } catch (Exception e) {
            log.warn("Agent LLM chat failed, falling back to rule mode: {}", e.getMessage());
            return ruleBasedChat(request.getMessage());
        }
    }

    /**
     * LLM 模式：调用 LangChain4j 生成回复
     */
    private String llmChat(ChatRequest request) {
        List<ChatMessage> messages = buildMessages(request);
        log.info("Agent LLM chat: provider={}", agentProperties.getProvider());
        // LangChain4j 0.36.x: ChatLanguageModel.generate(List<ChatMessage>) 返回 Response<AiMessage>
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        String result = response.content().text();
        log.debug("Agent LLM response: {}", result);
        return result;
    }

    /**
     * LLM 模式：单条 UserMessage 调用
     */
    private String llmGenerate(String prompt) {
        Response<AiMessage> response = chatLanguageModel.generate(UserMessage.from(prompt));
        return response.content().text();
    }

    private List<ChatMessage> buildMessages(ChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        if (request.getHistory() != null) {
            for (ChatRequest.ChatMessage m : request.getHistory()) {
                if ("user".equals(m.getRole())) {
                    messages.add(UserMessage.from(m.getContent()));
                } else {
                    messages.add(AiMessage.from(m.getContent()));
                }
            }
        }
        messages.add(UserMessage.from(request.getMessage()));
        return messages;
    }

    /**
     * 规则降级模式：根据消息关键词直接调用工具，返回 JSON 结果
     */
    private String ruleBasedChat(String message) {
        String msg = message != null ? message : "";
        log.info("Agent rule-based mode: keyword matching for '{}' ", msg);

        if (msg.contains("逾期") || msg.contains("overdue")) {
            return "{\"type\":\"overdue_orders\",\"note\":\"AI 服务暂不可用（未配置 API Key），以下为规则查询结果\",\"data\":"
                    + orderTool.queryOverdueOrders() + "}";
        }
        if (msg.contains("库存") || msg.contains("预警") || msg.contains("low stock") || msg.contains("安全库存")) {
            return "{\"type\":\"low_stock\",\"note\":\"AI 服务暂不可用（未配置 API Key），以下为规则查询结果\",\"data\":"
                    + inventoryTool.lowStockAlerts() + "}";
        }
        if (msg.contains("供应商") || msg.contains("应付") || msg.contains("余额") || msg.contains("付款")) {
            return "{\"type\":\"supplier_balance\",\"note\":\"AI 服务暂不可用（未配置 API Key），以下为规则查询结果\",\"data\":"
                    + settlementTool.supplierBalances() + "}";
        }
        if (msg.contains("报表") || msg.contains("统计") || msg.contains("排行") || msg.contains("金额")) {
            return "{\"type\":\"report_rank\",\"note\":\"AI 服务暂不可用（未配置 API Key），以下为规则查询结果\",\"data\":"
                    + reportTool.rank("supplier", 10) + "}";
        }
        if (msg.contains("汇总") || msg.contains("总额") || msg.contains("总订单")) {
            return "{\"type\":\"report_summary\",\"note\":\"AI 服务暂不可用（未配置 API Key），以下为规则查询结果\",\"data\":"
                    + reportTool.summary() + "}";
        }
        return "{\"note\":\"AI 服务暂不可用（未配置 DASHSCOPE_API_KEY 或 ZHIPU_API_KEY）。"
                + "请配置环境变量后重启服务以启用 LLM 模式。当前为规则模式，可识别关键词："
                + "逾期、库存/预警、供应商/应付/余额、报表/统计、汇总。\"}";
    }

    // ==================== 预警 ====================

    @Override
    public AgentAlertVO generateAlert(String alertType, Long refId, String refNo, String detail) {
        AgentAlertVO alert = new AgentAlertVO();
        alert.setAlertId(UUID.randomUUID().toString());
        alert.setAlertType(alertType);
        alert.setRefId(refId);
        alert.setRefNo(refNo);
        alert.setCreateTime(LocalDateTime.now());
        alert.setHandled(false);

        String title;
        String content;
        switch (alertType) {
            case "OVERDUE_ORDER":
                title = "逾期订单预警";
                content = detail != null ? detail : "存在期望到货日期已过的订单";
                alert.setLevel("WARNING");
                break;
            case "LOW_STOCK":
                title = "库存预警";
                content = detail != null ? detail : "库存低于安全库存";
                alert.setLevel("CRITICAL");
                break;
            case "PRICE_ABNORMAL":
                title = "价格异常预警";
                content = detail != null ? detail : "采购价格波动超过阈值";
                alert.setLevel("INFO");
                break;
            default:
                title = "系统预警";
                content = detail != null ? detail : "未知预警";
                alert.setLevel("INFO");
        }

        alert.setTitle(title);
        alert.setContent(content);

        // 若 LLM 可用，追加 AI 建议
        if (agentProperties.isAvailable()) {
            try {
                String aiAdvice = llmGenerate(
                        "你是采购系统预警助手。请根据以下预警信息生成简短建议（不超过50字）：\n"
                                + "类型：" + alertType + "\n详情：" + content
                );
                log.debug("AI alert advice: {}", aiAdvice);
                alert.setContent(content + "\n【AI建议】" + aiAdvice);
            } catch (Exception e) {
                log.warn("AI alert generation failed, using plain content: {}", e.getMessage());
            }
        }

        storeAlert(alert);
        log.warn("Alert triggered: type={} refId={} refNo={} level={}", alertType, refId, refNo, alert.getLevel());
        return alert;
    }

    private void storeAlert(AgentAlertVO alert) {
        try {
            String json = objectMapper.writeValueAsString(alert);
            // 存入 List
            redisTemplate.opsForList().leftPush(RedisKeys.alertList(), json);
            redisTemplate.expire(RedisKeys.alertList(), Duration.ofHours(1));
            // 存入 item（供 markHandled 使用）
            redisTemplate.opsForValue().set(RedisKeys.alertItem(alert.getAlertId()), json, Duration.ofHours(1));
            log.info("Alert stored: id={} type={}", alert.getAlertId(), alert.getAlertType());
        } catch (JsonProcessingException e) {
            log.error("Failed to store alert: {}", e.getMessage());
        }
    }

    @Override
    public List<AgentAlertVO> pendingAlerts() {
        try {
            List<String> rawList = redisTemplate.opsForList().range(RedisKeys.alertList(), 0, -1);
            if (rawList == null || rawList.isEmpty()) return List.of();

            List<AgentAlertVO> alerts = new ArrayList<>();
            for (String json : rawList) {
                AgentAlertVO alert = objectMapper.readValue(json, AgentAlertVO.class);
                if (!Boolean.TRUE.equals(alert.getHandled())) {
                    alerts.add(alert);
                }
            }
            // 最新在前
            alerts.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));
            return alerts;
        } catch (Exception e) {
            log.warn("Failed to fetch alerts: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void markAlertHandled(String alertId) {
        try {
            String json = redisTemplate.opsForValue().get(RedisKeys.alertItem(alertId));
            if (json == null) return;

            AgentAlertVO alert = objectMapper.readValue(json, AgentAlertVO.class);
            alert.setHandled(true);
            String updatedJson = objectMapper.writeValueAsString(alert);
            redisTemplate.opsForValue().set(RedisKeys.alertItem(alertId), updatedJson, Duration.ofHours(1));

            // 同步更新 List 中对应条目
            List<String> rawList = redisTemplate.opsForList().range(RedisKeys.alertList(), 0, -1);
            List<String> updatedList = new ArrayList<>();
            for (String item : rawList) {
                AgentAlertVO a = objectMapper.readValue(item, AgentAlertVO.class);
                if (a.getAlertId().equals(alertId)) {
                    updatedList.add(updatedJson);
                } else {
                    updatedList.add(item);
                }
            }
            if (!updatedList.isEmpty()) {
                // 用逐个 set 替代 set(key, T[])：StringRedisTemplate 不支持 set(key, String[])
                redisTemplate.delete(RedisKeys.alertList());
                for (String item : updatedList) {
                    redisTemplate.opsForList().rightPush(RedisKeys.alertList(), item);
                }
                redisTemplate.expire(RedisKeys.alertList(), Duration.ofHours(1));
            }
            log.info("Alert marked handled: id={}", alertId);
        } catch (Exception e) {
            log.warn("Failed to mark alert handled: {}", e.getMessage());
        }
    }

    // ==================== 辅助审批 / 发票校验 / 付款建议 ====================

    @Override
    public SuggestionVO suggestApproval(Long orderId, String orderContext) {
        SuggestionVO vo = new SuggestionVO();
        vo.setType("APPROVAL");
        vo.setRefId(orderId);
        vo.setAiGenerated(agentProperties.isAvailable());

        if (!agentProperties.isAvailable()) {
            vo.setSuggestion("AI 服务暂不可用（未配置 API Key）。请人工审核订单：" + orderContext);
            vo.setRisk("MEDIUM");
            return vo;
        }

        try {
            String prompt = "你是采购审批助手。请根据以下订单信息生成审批意见草稿（包含合理性判断、风险提示、建议）：\n"
                    + orderContext + "\n请用 JSON 返回，字段：suggestion（审批意见）、risk（LOW/MEDIUM/HIGH）";
            String response = llmGenerate(prompt);
            log.debug("AI approval suggestion: {}", response);
            parseSuggestionJson(response, vo);
        } catch (Exception e) {
            log.warn("AI approval suggestion failed: {}", e.getMessage());
            vo.setSuggestion("AI 服务暂不可用。请人工审核订单：" + orderContext);
            vo.setRisk("MEDIUM");
        }
        return vo;
    }

    @Override
    public SuggestionVO suggestInvoiceCheck(Long invoiceId, String invoiceContext) {
        SuggestionVO vo = new SuggestionVO();
        vo.setType("INVOICE_CHECK");
        vo.setRefId(invoiceId);
        vo.setAiGenerated(agentProperties.isAvailable());

        if (!agentProperties.isAvailable()) {
            vo.setSuggestion("AI 服务暂不可用（未配置 API Key）。请人工核对发票：" + invoiceContext);
            vo.setRisk("MEDIUM");
            return vo;
        }

        try {
            String prompt = "你是发票校验助手。请根据以下发票信息生成校验结果（金额合理性、税率合规性、与订单/收货差异）：\n"
                    + invoiceContext + "\n请用 JSON 返回，字段：suggestion（校验结果）、risk（LOW/MEDIUM/HIGH）";
            String response = llmGenerate(prompt);
            log.debug("AI invoice check: {}", response);
            parseSuggestionJson(response, vo);
        } catch (Exception e) {
            log.warn("AI invoice check failed: {}", e.getMessage());
            vo.setSuggestion("AI 服务暂不可用。请人工核对发票：" + invoiceContext);
            vo.setRisk("MEDIUM");
        }
        return vo;
    }

    @Override
    public SuggestionVO suggestPayment(Long invoiceId, String invoiceContext) {
        SuggestionVO vo = new SuggestionVO();
        vo.setType("PAYMENT");
        vo.setRefId(invoiceId);
        vo.setAiGenerated(agentProperties.isAvailable());

        if (!agentProperties.isAvailable()) {
            vo.setSuggestion("AI 服务暂不可用（未配置 API Key）。请人工决定付款：" + invoiceContext);
            vo.setRisk("MEDIUM");
            return vo;
        }

        try {
            String prompt = "你是付款建议助手。请根据以下发票信息生成付款建议（建议金额、付款方式、风险提示）：\n"
                    + invoiceContext + "\n请用 JSON 返回，字段：suggestion（付款建议）、risk（LOW/MEDIUM/HIGH）";
            String response = llmGenerate(prompt);
            log.debug("AI payment suggestion: {}", response);
            parseSuggestionJson(response, vo);
        } catch (Exception e) {
            log.warn("AI payment suggestion failed: {}", e.getMessage());
            vo.setSuggestion("AI 服务暂不可用。请人工决定付款：" + invoiceContext);
            vo.setRisk("MEDIUM");
        }
        return vo;
    }

    // ==================== 私有工具 ====================

    private void parseSuggestionJson(String response, SuggestionVO vo) {
        if (response == null || response.isBlank()) {
            vo.setSuggestion("AI 返回为空，请人工确认");
            vo.setRisk("MEDIUM");
            return;
        }
        try {
            var node = objectMapper.readTree(response);
            vo.setSuggestion(node.path("suggestion").asText(response));
            vo.setRisk(node.path("risk").asText("MEDIUM"));
        } catch (Exception e) {
            // 非 JSON 格式，原样存入
            vo.setSuggestion(response);
            vo.setRisk("MEDIUM");
        }
    }
}
