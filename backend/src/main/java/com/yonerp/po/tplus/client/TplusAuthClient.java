package com.yonerp.po.tplus.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonerp.po.tplus.config.TplusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** T+ 自建应用 Token 客户端。只输出脱敏状态，不记录密钥和 Token。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TplusAuthClient {

    private final TplusProperties properties;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    public String getOpenToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken == null || !Instant.now().isBefore(tokenExpiresAt)) {
                refresh();
            }
            return cachedToken;
        }
    }

    public void clear() {
        cachedToken = null;
        tokenExpiresAt = Instant.MIN;
    }

    private void refresh() {
        if (!properties.hasCredentials() || properties.getAccountId().isBlank()) {
            throw new IllegalStateException("T+ Token 配置不完整：需要 TPLUS_APP_KEY、TPLUS_APP_SECRET 和 TPLUS_ACCOUNT_ID");
        }
        if (properties.getTokenUrl().isBlank()) {
            throw new IllegalStateException("T+ Token 地址未配置");
        }

        RestClient client = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
                    setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
                }})
                .build();
        Map<String, String> body = Map.of(
                "appKey", properties.getAppKey(),
                "appSecret", properties.getAppSecret(),
                "accountId", properties.getAccountId()
        );

        try {
            JsonNode response = client.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.path("code").asInt() != 200) {
                throw new IllegalStateException("T+ Token 获取失败：" + responseMessage(response));
            }
            String token = response.path("data").path("openToken").asText("");
            long expire = response.path("data").path("expire").asLong(0);
            if (token.isBlank() || expire <= 0) {
                throw new IllegalStateException("T+ Token 响应缺少 openToken 或 expire");
            }
            cachedToken = token;
            tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expire - 300));
            log.info("T+ openToken refreshed, expiresInSeconds={}", expire);
        } catch (RestClientException ex) {
            throw new IllegalStateException("T+ Token 请求失败，请检查网络和应用凭证", ex);
        }
    }

    private String responseMessage(JsonNode response) {
        return response == null ? "empty response" : response.path("msg").asText("unknown error");
    }
}
