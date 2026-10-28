package com.yonerp.po.importx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Excel 导入行暂存表（校验通过前不写入业务表）
 *
 * <p>每个导入批次的所有行数据先写入此表，校验通过并用户确认后，再由各业务处理器写入正式表。
 */
@Data
@TableName("sys_import_row")
public class SysImportRow {

    private Long id;

    private Long batchId;

    /** Excel 行号（从数据行开始，不含表头） */
    private Integer rowIndex;

    /** 原始行数据 JSON（保持 Excel 列顺序与值） */
    private String rowData;

    /** PENDING/VALID/WARNING/ERROR */
    private String status;

    /** 业务 ID（确认成功后写入，用于回查） */
    private Long bizId;
}