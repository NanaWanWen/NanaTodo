package com.yonerp.po.importx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Excel 导入批次表
 *
 * <p>一次上传文件对应一条批次记录。批次状态流转：
 * UPLOADED → PARSING → VALIDATED → CONFIRMED / FAILED / CANCELED
 */
@Data
@TableName("sys_import_batch")
public class SysImportBatch {

    private Long id;

    /** 批次号 IMPyyyyMMddNNNN */
    private String batchNo;

    /** 业务类型 ORDER/RECEIPT/INVOICE/PAYMENT */
    private String bizType;

    /** 模板版本号，如 V1 */
    private String templateVersion;

    /** 原始文件名 */
    private String fileName;

    /** 文件 SHA-256 指纹，用于幂等识别重复上传 */
    private String fileFingerprint;

    /** UPLOADED/PARSING/VALIDATED/CONFIRMED/FAILED/CANCELED */
    private String status;

    private Integer totalRows;

    private Integer validRows;

    private Integer warningRows;

    private Integer errorRows;

    private String summary;

    private Long createBy;

    private LocalDateTime createTime;

    private Long confirmBy;

    private LocalDateTime confirmTime;
}
