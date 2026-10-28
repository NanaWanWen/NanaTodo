package com.yonerp.po.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    /** 注册 JSR310 模块，否则 LocalDate/LocalDateTime 序列化会失败 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
        query.setPageNo(1);
        query.setPageSize(50);
        // 查询候选审批中订单后，按 expectDate 判断是否逾期，不能用 endDate 过滤 orderDate。
        PageResult<OrderListVO> page = orderService.page(query);
        LocalDate today = LocalDate.now();
        List<OrderListVO> overdue = page.getRecords().stream()
                .filter(order -> order.getExpectDate() != null && order.getExpectDate().isBefore(today))
                .toList();
        return toJson(overdue);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }
}
