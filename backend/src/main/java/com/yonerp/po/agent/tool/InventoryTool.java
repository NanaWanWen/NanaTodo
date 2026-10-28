package com.yonerp.po.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yonerp.po.inventory.entity.PoInventory;
import com.yonerp.po.inventory.service.InventoryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存查询工具（LLM Tool）
 */
@Component
@RequiredArgsConstructor
public class InventoryTool {

    private final InventoryService inventoryService;
    /** 注册 JSR310 模块，否则 LocalDateTime 序列化会失败 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Tool("查询全量库存列表，含物料编码/名称/数量/安全库存")
    public String listInventory() {
        List<PoInventory> list = inventoryService.listAll();
        return toJson(list);
    }

    @Tool("查询低于安全库存的物料预警列表")
    public String lowStockAlerts() {
        List<PoInventory> list = inventoryService.lowStock();
        return toJson(list);
    }

    @Tool("查询指定物料库存详情")
    public String getInventory(@P("物料ID") Long materialId) {
        List<PoInventory> all = inventoryService.listAll();
        return all.stream()
                .filter(inv -> inv.getMaterialId().equals(materialId))
                .findFirst()
                .map(inv -> toJson(inv))
                .orElse("{\"error\":\"未找到该物料库存记录\"}");
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }
}
