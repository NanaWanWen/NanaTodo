package com.yonerp.po.report.controller;

import com.alibaba.excel.EasyExcel;
import com.yonerp.po.base.service.DictService;
import com.yonerp.po.base.vo.DictVO;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.LockService;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.Result;
import com.yonerp.po.order.dto.OrderQuery;
import com.yonerp.po.order.service.OrderService;
import com.yonerp.po.order.vo.OrderListVO;
import com.yonerp.po.report.excel.OrderExcelRow;
import com.yonerp.po.report.excel.RankExcelRow;
import com.yonerp.po.report.service.ReportService;
import com.yonerp.po.report.vo.RankResultVO;
import com.yonerp.po.report.vo.RankStatVO;
import com.yonerp.po.report.vo.ReportSummaryVO;
import com.yonerp.po.report.vo.StatusStatVO;
import com.yonerp.po.report.vo.TimeStatVO;
import com.yonerp.po.security.UserContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表接口
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    private final OrderService orderService;

    private final DictService dictService;

    private final LockService lockService;

    @GetMapping("/summary")
    public Result<ReportSummaryVO> summary(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.ok(reportService.summary(startDate, endDate));
    }

    @GetMapping("/by-status")
    public Result<List<StatusStatVO>> byStatus(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.ok(reportService.byStatus(startDate, endDate));
    }

    /**
     * 按时间统计：granularity = month | day
     */
    @GetMapping("/by-time")
    public Result<TimeStatVO> byTime(@RequestParam(defaultValue = "month") String granularity,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.ok(reportService.byTime(granularity, startDate, endDate));
    }

    /**
     * 维度排行统计：groupBy = supplier | buyer | material | dept
     * status 为空时按生效口径（审批中 + 已审批）统计；topN 默认 10，上限 100
     */
    @GetMapping("/rank")
    public Result<RankResultVO> rank(@RequestParam(defaultValue = "supplier") String groupBy,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "10") Integer topN) {
        return Result.ok(reportService.rank(groupBy, startDate, endDate, status, topN));
    }

    /**
     * 手动刷新：清空报表缓存（管理员与审批角色可用）
     */
    @GetMapping("/refresh")
    public Result<Void> refresh() {
        boolean allowed = lockService.tryAcquireRate(
                RedisKeys.rate(UserContext.currentUserId(), "report:refresh"), 10, Duration.ofMinutes(1));
        if (!allowed) {
            throw BizException.rateLimited();
        }
        reportService.summary(null, null);
        reportService.byStatus(null, null);
        reportService.byTime("month", null, null);
        reportService.rank("supplier", null, null, null, 10);
        return Result.ok();
    }

    /**
     * 导出统计表（当前维度，不截断 TOP N）
     */
    @GetMapping("/rank/export")
    public void exportRank(@RequestParam(defaultValue = "supplier") String groupBy,
                           @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                           @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                           @RequestParam(required = false) String status,
                           HttpServletResponse response) throws IOException {
        checkExportRate();
        RankResultVO rank = reportService.rankForExport(groupBy, startDate, endDate, status);

        List<RankExcelRow> rows = new ArrayList<>();
        int index = 0;
        for (RankStatVO item : rank.getRows()) {
            RankExcelRow row = new RankExcelRow();
            row.setIndex(++index);
            row.setCode(item.getCode());
            row.setName(item.getName());
            row.setOrderCount(item.getOrderCount());
            row.setQty(item.getQty());
            row.setAmount(item.getAmount());
            row.setPercent(item.getPercent());
            rows.add(row);
        }
        // 合计行
        RankExcelRow total = new RankExcelRow();
        total.setIndex(null);
        total.setCode("合计");
        total.setName("全口径");
        total.setOrderCount(rank.getTotal().getOrderCount());
        total.setQty(rank.getTotal().getQty());
        total.setAmount(rank.getTotal().getAmount());
        total.setPercent(new BigDecimal("100"));
        rows.add(total);

        String fileName = "采购统计表_" + rank.getGroupBy();
        writeExcel(response, fileName, "统计表", RankExcelRow.class, rows);
    }

    /**
     * 导出订单明细（与列表筛选条件一致，上限 1000 行）
     */
    @GetMapping("/orders/export")
    public void exportOrders(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) Long supplierId,
                             @RequestParam(required = false) Long buyerId,
                             @RequestParam(required = false) Long deptId,
                             @RequestParam(required = false) Long materialId,
                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                             HttpServletResponse response) throws IOException {
        checkExportRate();
        OrderQuery query = new OrderQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setSupplierId(supplierId);
        query.setBuyerId(buyerId);
        query.setDeptId(deptId);
        query.setMaterialId(materialId);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setPageNo(1);
        query.setPageSize(1000);

        PageResult<OrderListVO> page = orderService.page(query);
        Map<String, String> statusNames = new HashMap<>();
        for (DictVO dict : dictService.list("order_status")) {
            statusNames.put(dict.getDictCode(), dict.getDictName());
        }

        List<OrderExcelRow> rows = new ArrayList<>();
        for (OrderListVO order : page.getRecords()) {
            OrderExcelRow row = new OrderExcelRow();
            row.setOrderNo(order.getOrderNo());
            row.setTitle(order.getTitle());
            row.setSupplierName(order.getSupplierName());
            row.setBuyerName(order.getBuyerName());
            row.setOrderDate(order.getOrderDate() == null ? null : order.getOrderDate().toString());
            row.setTotalQty(order.getTotalQty());
            row.setTotalAmount(order.getTotalAmount());
            row.setStatusName(statusNames.getOrDefault(order.getStatus(), order.getStatus()));
            row.setCurrentNode(order.getCurrentNode() == null || order.getCurrentNode() == 0
                    ? "-"
                    : (order.getCurrentNode() == 1 ? "采购经理" : "总监"));
            rows.add(row);
        }
        writeExcel(response, "采购订单明细", "订单明细", OrderExcelRow.class, rows);
    }

    /** 导出限流：60 秒内最多 5 次 */
    private void checkExportRate() {
        boolean allowed = lockService.tryAcquireRate(
                RedisKeys.rate(UserContext.currentUserId(), "report:export"), 5, Duration.ofSeconds(60));
        if (!allowed) {
            throw BizException.rateLimited();
        }
    }

    private <T> void writeExcel(HttpServletResponse response, String fileName, String sheetName,
                                Class<T> head, List<T> rows) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + encoded + ".xlsx");
        EasyExcel.write(response.getOutputStream(), head).sheet(sheetName).doWrite(rows);
    }
}
