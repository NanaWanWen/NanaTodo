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
import java.util.LinkedHashMap;
import java.util.Map;

/** T+ Cloud 只读客户端。此类不提供任何写入 T+ 业务数据的方法。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TplusClient {

    private final TplusProperties properties;
    private final ObjectMapper objectMapper;
    private final TplusAuthClient authClient;

    public JsonNode querySuppliers(int pageIndex) {
        return queryPage(properties.getSupplierPath(), pageIndex);
    }

    public JsonNode queryMaterials(int pageIndex) {
        return queryPage(properties.getMaterialPath(), pageIndex);
    }

    public JsonNode queryPurchaseOrders(int pageIndex) {
        return queryPage(properties.getPurchaseOrderPath(), pageIndex);
    }

    public JsonNode queryInventory(int pageIndex) {
        return queryPage(properties.getInventoryPath(), pageIndex);
    }

    public boolean isConfigured() {
        return properties.isEnabled()
                && properties.hasCredentials()
                && !properties.getAccountId().isBlank()
                && !properties.getBaseUrl().isBlank();
    }

    private JsonNode queryPage(String path, int pageIndex) {
        if (!isConfigured()) {
            throw new IllegalStateException("T+ 同步未配置完成：需要 TPLUS_ENABLED、appKey、appSecret、accountId 和 baseUrl");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("T+ 查询接口路径未配置");
        }

        Map<String, Object> param = new LinkedHashMap<>();
        param.put("PageSize", properties.getPageSize());
        param.put("PageIndex", pageIndex);
        param.put("SelectFields", "ID,Code,Name,Disabled,Ts");

        Map<String, Object> body = Map.of("param", param);
        RestClient client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
                    setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
                }})
                .build();

        int attempts = Math.max(0, properties.getMaxRetries()) + 1;
        RestClientException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JsonNode result = client.post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("appKey", properties.getAppKey())
                        .header("appSecret", properties.getAppSecret())
                        .header("openToken", authClient.getOpenToken())
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                log.debug("T+ read-only query succeeded: path={}, page={}", path, pageIndex);
                return result == null ? objectMapper.createObjectNode() : result;
            } catch (RestClientException ex) {
                last = ex;
                if (attempt < attempts) {
                    sleep(attempt);
                }
            }
        }
        log.warn("T+ read-only query failed: path={}, page={}, attempts={}", path, pageIndex, attempts);
        throw last == null ? new IllegalStateException("T+ 查询失败") : last;
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(Math.min(1000L * attempt, 3000L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("T+ 查询重试被中断", ex);
        }
    }

}
