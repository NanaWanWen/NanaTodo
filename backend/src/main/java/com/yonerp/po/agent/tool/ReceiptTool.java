package com.yonerp.po.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.receipt.dto.ReceiptQuery;
import com.yonerp.po.receipt.service.ReceiptService;
import com.yonerp.po.receipt.vo.ReceiptListVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 收货单查询工具（LLM Tool）
 */
@Component
@RequiredArgsConstructor
public class ReceiptTool {

    private final ReceiptService receiptService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool("查询收货单列表，可按订单ID/状态筛选")
    public String queryReceipts(@P("订单ID，可空") Long orderId,
                                @P("状态：DRAFT/CONFIRMED/CANCELED，可空") String status) {
        ReceiptQuery query = new ReceiptQuery();
        if (orderId != null) query.setOrderId(orderId);
        if (status != null && !status.isBlank()) query.setStatus(status);
        query.setPageNo(1);
        query.setPageSize(20);
        PageResult<ReceiptListVO> page = receiptService.page(query);
        return toJson(page);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }
}
