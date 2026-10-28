package com.yonerp.po.agent.tool;

import com.yonerp.po.report.service.ReportService;
import com.yonerp.po.report.vo.RankResultVO;
import com.yonerp.po.report.vo.ReportSummaryVO;
import com.yonerp.po.report.vo.StatusStatVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表查询工具（LLM Tool）
 * <p>
 * 数据权限：ReportService 内部通过 UserContext 判断范围，采购员仅统计本人数据。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportTool {

    private final ReportService reportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool("查询采购报表汇总数据，包括订单总数、总金额、各状态数量")
    public String summary() {
        return summary(LocalDate.now().minusMonths(6), LocalDate.now());
    }

    @Tool("按指定日期区间查询采购报表汇总数据。当用户提到\"本月/上月/今年/去年/近三个月/近一周\"等时间范围时，必须用本工具并传入推断出的起止日期，不要用无参版本")
    public String summaryByRange(@P("开始日期，格式 yyyy-MM-dd") String startDate,
                                 @P("结束日期，格式 yyyy-MM-dd") String endDate) {
        return summary(parseDate(startDate), parseDate(endDate));
    }

    public String summary(LocalDate startDate, LocalDate endDate) {
        ReportSummaryVO summary = reportService.summary(startDate, endDate);
        return toJson(summary);
    }

    @Tool("按状态统计采购订单数量与金额，时间范围默认近6个月")
    public String byStatus() {
        return byStatus(LocalDate.now().minusMonths(6), LocalDate.now());
    }

    @Tool("按指定日期区间、按状态统计采购订单数量与金额。用户指定\"本月/上月/近三个月\"等时间范围时必须用本工具并传入推断出的起止日期")
    public String byStatusByRange(@P("开始日期，格式 yyyy-MM-dd") String startDate,
                                  @P("结束日期，格式 yyyy-MM-dd") String endDate) {
        return byStatus(parseDate(startDate), parseDate(endDate));
    }

    public String byStatus(LocalDate startDate, LocalDate endDate) {
        List<StatusStatVO> list = reportService.byStatus(startDate, endDate);
        return toJson(list);
    }

    @Tool("按供应商/采购员/物料/部门维度统计采购金额排行。groupBy 取值：supplier、buyer、material、dept")
    public String rank(@P("分组维度：supplier/buyer/material/dept") String groupBy,
                       @P("TOP N，默认10") Integer topN) {
        return rank(groupBy, LocalDate.now().minusMonths(6), LocalDate.now(), topN);
    }

    public String rank(String groupBy, LocalDate startDate, LocalDate endDate, Integer topN) {
        RankResultVO result = reportService.rank(groupBy, startDate, endDate, null, topN != null ? topN : 10);
        return toJson(result);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            log.warn("ReportTool 日期参数解析失败：{}，回退为当天", value);
            return LocalDate.now();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }
}
