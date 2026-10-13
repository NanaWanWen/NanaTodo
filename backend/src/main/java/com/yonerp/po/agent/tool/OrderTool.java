package com.yonerp.po.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.order.dto.OrderQuery;
import com.yonerp.po.order.service.OrderService;
import com.yonerp.po.order.vo.OrderListVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 订单查询工具（LLM Tool）
 */
@Component
@RequiredArgsConstructor
public class OrderTool {

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool("查询采购订单列表，可按状态/供应商/日期筛选")
    public String queryOrders(@P("订单状态：DRAFT/APPROVING/APPROVED/REJECTED/CANCELED/COMPLETED，可空") String status,
                              @P("供应商ID，可空") Long supplierId,
                              @P("期望到货日期之前：yyyy-MM-dd，可空") String beforeDate) {
        OrderQuery query = new OrderQuery();
        if (status != null && !status.isBlank()) query.setStatus(status);
        if (supplierId != null) query.setSupplierId(supplierId);
        if (beforeDate != null && !beforeDate.isBlank()) {
            try { query.setEndDate(LocalDate.parse(beforeDate)); } catch (Exception ignored) {}
        }
        query.setPageSize(20);
        PageResult<OrderListVO> page = orderService.page(query);
        return toJson(page);
    }

    @Tool("查询逾期订单：期望到货日期已过但仍处于审批中状态的订单")
    public String queryOverdueOrders() {
        OrderQuery query = new OrderQuery();
        query.setStatus("APPROVING");
        query.setEndDate(LocalDate.now().minusDays(1));
        query.setPageNo(1);
        query.setPageSize(50);
        PageResult<OrderListVO> page = orderService.page(query);
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
