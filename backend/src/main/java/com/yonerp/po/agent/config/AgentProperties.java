package com.yonerp.po.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "yonerp.agent")
public class AgentProperties {

    /** LLM 提供商：dashscope / zhipu */
    private String provider = "dashscope";

    private String dashscopeApiKey = "";

    private String dashscopeModel = "qwen-max";

    private String zhipuApiKey = "";

    private String zhipuModel = "glm-4-plus";

    private int timeout = 60;

    private int alertScanIntervalMinutes = 5;

    /** 获取当前提供商的 API Key */
    public String currentApiKey() {
        return "zhipu".equals(provider) ? zhipuApiKey : dashscopeApiKey;
    }

    public boolean isAvailable() {
        return currentApiKey() != null && !currentApiKey().isBlank();
    }
}
