package com.yonerp.po.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonerp.po.ap.service.ApSettlementService;
import com.yonerp.po.ap.vo.SupplierBalanceVO;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应付对账工具（LLM Tool）
 */
@Component
@RequiredArgsConstructor
public class SettlementTool {

    private final ApSettlementService apSettlementService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool("查询供应商应付余额：未付款发票金额、已付款金额、应付余额")
    public String supplierBalances() {
        List<SupplierBalanceVO> list = apSettlementService.supplierBalances();
        return toJson(list);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }
}
