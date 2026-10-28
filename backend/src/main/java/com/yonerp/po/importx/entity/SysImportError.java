package com.yonerp.po.importx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Excel 导入错误记录
 *
 * <p>记录校验失败的行、字段、原始值、错误级别和修复建议，供前端展示错误表格。
 */
@Data
@TableName("sys_import_error")
public class SysImportError {

    private Long id;

    private Long batchId;

    private Integer rowIndex;

    /** 字段名（Excel 列标题或业务字段名） */
    private String fieldName;

    /** Excel 原始值 */
    private String rawValue;

    /** ERROR：阻断性错误；WARNING：非阻断但需注意 */
    private String errorLevel;

    /** 错误码（如 REQUIRED_FIELD_MISSING / SUPPLIER_NOT_FOUND） */
    private String errorCode;

    /** 错误描述 */
    private String errorMessage;

    /** 修复建议 */
    private String suggestion;
}