package com.yonerp.po.importx.service;

import com.yonerp.po.importx.core.ImportBizHandler;
import com.yonerp.po.importx.core.ImportBizType;
import com.yonerp.po.importx.entity.SysImportBatch;
import com.yonerp.po.importx.entity.SysImportError;
import com.yonerp.po.importx.entity.SysImportRow;
import com.yonerp.po.importx.mapper.SysImportBatchMapper;
import com.yonerp.po.importx.mapper.SysImportErrorMapper;
import com.yonerp.po.importx.mapper.SysImportRowMapper;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Excel 导入服务（通用框架）
 *
 * <p>流程：上传 → 创建批次 → 流式解析 → 暂存数据 → 分层校验 → 预览 → 用户确认 → 事务导入。
 *
 * <p>解析阶段只写 sys_import_row 和 sys_import_error，不写业务表；
 * 确认阶段调用各业务处理器，在事务中写入正式表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final SysImportBatchMapper batchMapper;
    private final SysImportRowMapper rowMapper;
    private final SysImportErrorMapper errorMapper;
    private final Map<ImportBizType, ImportBizHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 注册业务处理器（Spring 容器启动时自动注入所有 ImportBizHandler Bean）
     */
    public void registerHandler(ImportBizHandler handler) {
        handlers.put(handler.bizType(), handler);
        log.info("注册导入处理器：{} - {}", handler.bizType().getLabel(), handler.getClass().getSimpleName());
    }

    /**
     * 上传并解析 Excel 文件。
     *
     * @return 批次号
     */
    @Transactional(rollbackFor = Exception.class)
    public String uploadAndParse(MultipartFile file, String bizType, Long userId) {
        ImportBizType type = ImportBizType.of(bizType);
        ImportBizHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("未注册导入处理器：" + bizType);
        }

        // 1. 计算文件指纹
        String fingerprint = sha256(file);

        // 2. 创建批次记录
        String batchNo = generateBatchNo();
        SysImportBatch batch = new SysImportBatch();
        batch.setBatchNo(batchNo);
        batch.setBizType(type.getCode());
        batch.setTemplateVersion(handler.templateVersion());
        batch.setFileName(file.getOriginalFilename());
        batch.setFileFingerprint(fingerprint);
        batch.setStatus("PARSING");
        batch.setCreateBy(userId);
        batchMapper.insert(batch);

        // 3. 流式解析 Excel
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            List<String> headers = handler.templateHeaders();

            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    // 校验表头是否匹配模板（可选）
                    // 实际项目中这里可以加入表头匹配逻辑
                }

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    // 将列索引映射到列标题
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.size() && i < data.size(); i++) {
                        row.put(headers.get(i), data.get(i));
                    }
                    rows.add(row);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 解析完成
                }
            }).sheet().doRead();

            // 4. 暂存行数据
            int rowIndex = 1;
            for (Map<String, String> row : rows) {
                SysImportRow importRow = new SysImportRow();
                importRow.setBatchId(batch.getId());
                importRow.setRowIndex(rowIndex++);
                importRow.setRowData(toJson(row));
                importRow.setStatus("PENDING");
                rowMapper.insert(importRow);
            }

            batch.setTotalRows(rows.size());
            batch.setStatus("UPLOADED");
            batchMapper.updateById(batch);

            log.info("上传解析完成：batchNo={}, rows={}", batchNo, rows.size());
            return batchNo;

        } catch (Exception e) {
            batch.setStatus("FAILED");
            batch.setSummary("解析失败：" + e.getMessage());
            batchMapper.updateById(batch);
            throw new RuntimeException("Excel 解析失败", e);
        }
    }

    /**
     * 分层校验（文件级 → 行级 → 业务规则级）
     *
     * @return 错误行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int validate(String batchNo) {
        SysImportBatch batch = requireBatch(batchNo);
        ImportBizHandler handler = requireHandler(batch.getBizType());

        List<SysImportRow> rows = rowMapper.selectList(
                new LambdaQueryWrapper<SysImportRow>().eq(SysImportRow::getBatchId, batch.getId()));

        int errorCount = 0;
        int warningCount = 0;

        for (SysImportRow row : rows) {
            Map<String, String> rowData = fromJson(row.getRowData());
            List<SysImportError> errors = handler.validateRow(row.getRowIndex(), rowData);

            if (!errors.isEmpty()) {
                for (SysImportError error : errors) {
                    error.setBatchId(batch.getId());
                    errorMapper.insert(error);

                    if ("ERROR".equals(error.getErrorLevel())) {
                        errorCount++;
                    } else {
                        warningCount++;
                    }
                }
                row.setStatus(errors.stream().anyMatch(e -> "ERROR".equals(e.getErrorLevel())) ? "ERROR" : "WARNING");
            } else {
                row.setStatus("VALID");
            }
            rowMapper.updateById(row);
        }

        batch.setErrorRows(errorCount);
        batch.setWarningRows(warningCount);
        batch.setValidRows(batch.getTotalRows() - errorCount);
        batch.setStatus("VALIDATED");
        batchMapper.updateById(batch);

        log.info("校验完成：batchNo={}, errors={}, warnings={}", batchNo, errorCount, warningCount);
        return errorCount;
    }

    /**
     * 确认导入（仅在校验通过后调用）
     *
     * @return 成功导入行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int confirm(String batchNo, Long userId) {
        SysImportBatch batch = requireBatch(batchNo);
        if (!"VALIDATED".equals(batch.getStatus())) {
            throw new IllegalStateException("批次未完成校验，无法确认");
        }
        if (batch.getErrorRows() > 0) {
            throw new IllegalStateException("存在阻断性错误，请修正后重新上传");
        }

        ImportBizHandler handler = requireHandler(batch.getBizType());

        // 只处理校验通过的行
        List<SysImportRow> rows = rowMapper.selectList(
                new LambdaQueryWrapper<SysImportRow>()
                        .eq(SysImportRow::getBatchId, batch.getId())
                        .in(SysImportRow::getStatus, "VALID", "WARNING"));

        List<Map<String, String>> validRows = new ArrayList<>();
        for (SysImportRow row : rows) {
            validRows.add(fromJson(row.getRowData()));
        }

        // 调用业务处理器在事务中导入
        List<Long> bizIds = handler.confirm(validRows);

        // 回填业务 ID
        for (int i = 0; i < rows.size() && i < bizIds.size(); i++) {
            SysImportRow row = rows.get(i);
            row.setBizId(bizIds.get(i));
            rowMapper.updateById(row);
        }

        batch.setStatus("CONFIRMED");
        batch.setConfirmBy(userId);
        batch.setConfirmTime(LocalDateTime.now());
        batch.setSummary("成功导入 " + bizIds.size() + " 行");
        batchMapper.updateById(batch);

        log.info("导入成功：batchNo={}, rows={}", batchNo, bizIds.size());
        return bizIds.size();
    }

    /**
     * 获取批次详情（供前端预览）
     */
    public SysImportBatch detail(String batchNo) {
        return requireBatch(batchNo);
    }

    /**
     * 获取批次的错误列表（供前端展示错误表格）
     */
    public List<SysImportError> errors(String batchNo) {
        SysImportBatch batch = requireBatch(batchNo);
        return errorMapper.selectList(
                new LambdaQueryWrapper<SysImportError>().eq(SysImportError::getBatchId, batch.getId()));
    }

    // ==================== 私有方法 ====================

    private SysImportBatch requireBatch(String batchNo) {
        SysImportBatch batch = batchMapper.selectOne(
                new LambdaQueryWrapper<SysImportBatch>().eq(SysImportBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new IllegalArgumentException("批次不存在：" + batchNo);
        }
        return batch;
    }

    private ImportBizHandler requireHandler(String bizType) {
        ImportBizType type = ImportBizType.of(bizType);
        ImportBizHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("未注册导入处理器：" + bizType);
        }
        return handler;
    }

    private String generateBatchNo() {
        return "IMP" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%04d", System.currentTimeMillis() % 10000);
    }

    private String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String toJson(Map<String, String> map) {
        // 简单 JSON 序列化（生产环境可用 Jackson）
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"")
                    .append(entry.getValue() != null ? entry.getValue().replace("\"", "\\\"") : "").append("\"");
            first = false;
        }
        return sb.append("}").toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fromJson(String json) {
        // 简单 JSON 反序列化（生产环境可用 Jackson）
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.length() < 2) return map;
        json = json.substring(1, json.length() - 1); // 去掉 {}
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            int idx = pair.indexOf(':');
            if (idx > 0) {
                String key = pair.substring(0, idx).replace("\"", "").trim();
                String value = pair.substring(idx + 1).replace("\"", "").replace("\\\"", "\"").trim();
                map.put(key, value);
            }
        }
        return map;
    }
}