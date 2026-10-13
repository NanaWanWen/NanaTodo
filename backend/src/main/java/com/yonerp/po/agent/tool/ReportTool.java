package com.yonerp.po.agent.tool;

import com.yonerp.po.report.service.ReportService;
import com.yonerp.po.report.vo.RankResultVO;
import com.yonerp.po.report.vo.ReportSummaryVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 报表查询工具（LLM Tool）
 */
@Component
@RequiredArgsConstructor
public class ReportTool {

    private final ReportService reportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool("查询采购报表汇总数据，包括订单总数、总金额、各状态数量")
    public String summary() {
        ReportSummaryVO summary = reportService.summary(java.time.LocalDate.now().minusMonths(1), java.time.LocalDate.now());
        return toJson(summary);
    }

    @Tool("按供应商/采购员/物料/部门维度统计采购金额排行。groupBy 取值：supplier、buyer、material、dept")
    public String rank(@P("分组维度：supplier/buyer/material/dept") String groupBy,
                       @P("TOP N，默认10") Integer topN) {
        RankResultVO result = reportService.rank(groupBy, java.time.LocalDate.now().minusMonths(1), java.time.LocalDate.now(), null, topN != null ? topN : 10);
        return toJson(result);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }
}
