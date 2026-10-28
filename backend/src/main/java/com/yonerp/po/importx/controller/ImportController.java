package com.yonerp.po.importx.controller;

import com.yonerp.po.common.Result;
import com.yonerp.po.importx.core.ImportBizHandler;
import com.yonerp.po.importx.core.ImportBizType;
import com.yonerp.po.importx.entity.SysImportBatch;
import com.yonerp.po.importx.entity.SysImportError;
import com.yonerp.po.importx.service.ImportService;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.security.UserContext;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.security.RequireRole;
import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 导入接口
 *
 * <p>统一流程：下载模板 → 上传解析 → 校验 → 预览 → 确认导入。
 */
@Slf4j
@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /**
     * 下载导入模板（含表头与示例行）
     */
    @GetMapping("/templates/{bizType}")
    public void downloadTemplate(@PathVariable String bizType, HttpServletResponse response) throws IOException {
        ImportBizType type = ImportBizType.of(bizType);
        List<String> headers = templateHeaders(type);

        List<List<String>> head = new ArrayList<>();
        headers.forEach(h -> head.add(List.of(h)));
        List<List<String>> data = new ArrayList<>();
        data.add(exampleRow(type));

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = URLEncoder.encode(type.getLabel() + "导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);

        EasyExcel.write(response.getOutputStream())
                .head(head)
                .sheet(type.getLabel() + "模板")
                .doWrite(data);
    }

    /**
     * 上传并解析 Excel
     */
    @PostMapping("/upload")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam("bizType") String bizType) {
        CurrentUser user = UserContext.required();
        String batchNo = importService.uploadAndParse(file, bizType, user.getUserId());
        int errorCount = importService.validate(batchNo);

        SysImportBatch batch = importService.detail(batchNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("totalRows", batch.getTotalRows());
        result.put("validRows", batch.getValidRows());
        result.put("warningRows", batch.getWarningRows());
        result.put("errorRows", errorCount);
        result.put("canConfirm", errorCount == 0);
        return Result.ok(result);
    }

    /**
     * 获取批次详情
     */
    @GetMapping("/batches/{batchNo}")
    public Result<SysImportBatch> detail(@PathVariable String batchNo) {
        return Result.ok(importService.detail(batchNo));
    }

    /**
     * 获取批次错误列表（供错误表格展示与错误报告下载）
     */
    @GetMapping("/batches/{batchNo}/errors")
    public Result<List<SysImportError>> errors(@PathVariable String batchNo) {
        return Result.ok(importService.errors(batchNo));
    }

    /**
     * 下载错误报告
     */
    @GetMapping("/batches/{batchNo}/errors/report")
    public void downloadErrorReport(@PathVariable String batchNo, HttpServletResponse response) throws IOException {
        List<SysImportError> errors = importService.errors(batchNo);

        List<List<String>> head = new ArrayList<>();
        List.of("行号", "字段", "原始值", "级别", "错误码", "错误描述", "修复建议").forEach(h -> head.add(List.of(h)));

        List<List<String>> data = new ArrayList<>();
        for (SysImportError error : errors) {
            data.add(List.of(
                    String.valueOf(error.getRowIndex()),
                    nullToEmpty(error.getFieldName()),
                    nullToEmpty(error.getRawValue()),
                    nullToEmpty(error.getErrorLevel()),
                    nullToEmpty(error.getErrorCode()),
                    nullToEmpty(error.getErrorMessage()),
                    nullToEmpty(error.getSuggestion())
            ));
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = URLEncoder.encode("导入错误报告_" + batchNo + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);

        EasyExcel.write(response.getOutputStream()).head(head).sheet("错误报告").doWrite(data);
    }

    /**
     * 确认导入（事务导入）
     */
    @PostMapping("/batches/{batchNo}/confirm")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Map<String, Object>> confirm(@PathVariable String batchNo) {
        CurrentUser user = UserContext.required();
        int count = importService.confirm(batchNo, user.getUserId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("importedRows", count);
        return Result.ok(result);
    }

    // ==================== 私有方法 ====================

    private List<String> templateHeaders(ImportBizType type) {
        return switch (type) {
            case ORDER -> List.of("订单标题", "供应商名称", "订单日期", "期望到货日期", "物料编码", "数量", "单价", "税率", "备注");
            case RECEIPT -> List.of("订单编号", "收货日期", "实收数量", "备注");
            case INVOICE -> List.of("收货单编号", "发票号码", "开票日期", "备注");
            case PAYMENT -> List.of("发票号码", "付款日期", "付款金额", "付款方式", "备注");
        };
    }

    private List<String> exampleRow(ImportBizType type) {
        return switch (type) {
            case ORDER -> List.of("2026-04-14 A4纸采购", "晨光办公用品有限公司", "2026-04-14", "2026-04-24", "MAT001", "100", "25.00", "13", "示例行，请删除");
            case RECEIPT -> List.of("PO202604140001", "2026-04-19", "100", "示例行，请删除");
            case INVOICE -> List.of("RCPT202604190001", "INV202604220001", "2026-04-22", "示例行，请删除");
            case PAYMENT -> List.of("INV202604220001", "2026-04-26", "2825.00", "BANK_TRANSFER", "示例行，请删除");
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
