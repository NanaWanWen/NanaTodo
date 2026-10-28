package com.yonerp.po.invoice.dto;

import lombok.Data;

/**
 * 红冲（开具红字发票抵消原蓝票）请求
 */
@Data
public class InvoiceRedFlushCmd {

    /** 红冲原因（如：发票单价开错，供应商将重开） */
    private String remark;
}
