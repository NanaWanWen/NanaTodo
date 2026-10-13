package com.yonerp.po.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 配置：根据 AgentProperties 创建 ChatLanguageModel Bean
 * <p>
 * 支持两种提供商：
 * - dashscope（默认）：通义千问，通过 langchain4j-dashscope
 * - zhipu：智谱 GLM，通过 OpenAI 兼容接口（langchain4j-open-ai）
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

        log.info("LangChain4j: initializing Qwen model [{}]", agentProperties.getDashscopeModel());
        return QwenChatModel.builder()
                .apiKey(agentProperties.getDashscopeApiKey())
                .modelName(agentProperties.getDashscopeModel())
                .temperature(0.7f)
                .maxTokens(2048)
                .build();
    }
}
