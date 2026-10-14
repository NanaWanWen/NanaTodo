package com.yonerp.po.tplus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yonerp.po.tplus.client.TplusClient;
import com.yonerp.po.tplus.config.TplusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** T+ 只读同步编排。第一阶段只拉取并预览，不写入本地业务表。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TplusSyncService {

    private final TplusProperties properties;
    private final TplusClient client;

    public Map<String, Object> preview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("dryRun", properties.isDryRun());
        result.put("enterpriseId", properties.getEnterpriseId());
        result.put("configured", client.isConfigured());
        result.put("readOnly", true);

        if (!client.isConfigured()) {
            result.put("message", "T+ 同步默认关闭或凭证未配置；未发起外部请求");
            return result;
        }

        result.put("suppliers", summarize(client.querySuppliers(1)));
        if (!properties.getMaterialPath().isBlank()) {
            result.put("materials", summarize(client.queryMaterials(1)));
        }
        if (!properties.getPurchaseOrderPath().isBlank()) {
            result.put("purchaseOrders", summarize(client.queryPurchaseOrders(1)));
        }
        if (!properties.getInventoryPath().isBlank()) {
            result.put("inventory", summarize(client.queryInventory(1)));
        }
        result.put("message", "仅完成第一页只读预览，未写入 YonErp 数据库");
        return result;
    }

    private Map<String, Object> summarize(JsonNode response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        JsonNode result = response.path("result");
        summary.put("totalCount", result.path("TotalCount").asInt(0));
        summary.put("totalPage", result.path("TotalPage").asInt(0));
        summary.put("currentPage", result.path("CurrentPage").asInt(0));
        summary.put("dataCount", result.path("Data").isArray() ? result.path("Data").size() : 0);
        return summary;
    }
}
