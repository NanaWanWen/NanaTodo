package com.yonerp.po.agent.config;

import com.yonerp.po.agent.ai.PurchaseAgent;
import com.yonerp.po.agent.tool.InventoryTool;
import com.yonerp.po.agent.tool.OrderTool;
import com.yonerp.po.agent.tool.ReportTool;
import com.yonerp.po.agent.tool.SettlementTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * LangChain4j 配置：根据 AgentProperties 创建 ChatLanguageModel 与 AI Agent Bean
 * <p>
 * 支持两种提供商（均走 OpenAI 兼容协议，保证 Function Calling 可用）：
 * - dashscope（默认）：通义千问，compatible-mode 端点
 * - zhipu：智谱 GLM
 * </p>
 * <p>
 * 注意：若当前提供商未配置 API Key，则 Bean 为 null，
 * AgentService 会自动降级为规则模式，不影响系统启动。
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LangChain4jConfig {

    private final AgentProperties agentProperties;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (!agentProperties.isAvailable()) {
            log.info("LangChain4j: no API key configured for provider [{}], Agent will run in rule-based fallback mode",
                    agentProperties.getProvider());
            return null;
        }

        int timeout = agentProperties.getTimeout();

        if ("zhipu".equals(agentProperties.getProvider())) {
            log.info("LangChain4j: initializing OpenAI-compatible model [{}] for Zhipu GLM", agentProperties.getZhipuModel());
            return OpenAiChatModel.builder()
                    .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                    .apiKey(agentProperties.getZhipuApiKey())
                    .modelName(agentProperties.getZhipuModel())
                    .temperature(0.7)
                    .maxTokens(2048)
                    .timeout(Duration.ofSeconds(timeout))
                    .build();
        }

        // 通义千问 OpenAI 兼容端点：支持 Function Calling，供 AiServices 编排业务工具
        log.info("LangChain4j: initializing DashScope OpenAI-compatible model [{}]",
                agentProperties.getDashscopeModel());
        return OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(agentProperties.getDashscopeApiKey())
                .modelName(agentProperties.getDashscopeModel())
                .temperature(0.3)
                .maxTokens(2048)
                .timeout(Duration.ofSeconds(timeout))
                .build();
    }

    /**
     * 采购领域 AI Agent：将业务工具注册给大模型，使其自主编排调用（Tool Calling）
     * <p>
     * 未配置 API Key 时返回 null，AgentService 自动降级为规则模式。
     * </p>
     */
    @Bean
    public PurchaseAgent purchaseAgent(@Nullable ChatLanguageModel chatLanguageModel,
                                       OrderTool orderTool,
                                       ReportTool reportTool,
                                       InventoryTool inventoryTool,
                                       SettlementTool settlementTool) {
        if (chatLanguageModel == null) {
            log.info("LangChain4j: purchaseAgent disabled (no chat model), rule-based fallback will be used");
            return null;
        }
        log.info("LangChain4j: building PurchaseAgent with tools [OrderTool, ReportTool, InventoryTool, SettlementTool]");
        return AiServices.builder(PurchaseAgent.class)
                .chatLanguageModel(chatLanguageModel)
                // 包装 null 安全执行器：通义千问会给可选参数显式传 null，langchain4j 0.36.2 会 NPE
                .tools(com.yonerp.po.agent.ai.NullSafeTools.build(
                        List.of(orderTool, reportTool, inventoryTool, settlementTool)))
                .build();
    }
}
