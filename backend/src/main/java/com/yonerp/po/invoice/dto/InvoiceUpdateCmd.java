package com.yonerp.po.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 修改发票请求（仅允许修改未核对发票的金额、日期、备注）
 */
@Data
public class InvoiceUpdateCmd {

    @NotNull(message = "不含税金额不能为空")
    @DecimalMin(value = "0.00", inclusive = true, message = "不含税金额不能为负")
    private BigDecimal invoiceAmount;

    @NotNull(message = "税额不能为空")
    @DecimalMin(value = "0.00", inclusive = true, message = "税额不能为负")
    private BigDecimal taxAmount;

    private LocalDate invoiceDate;

    private String remark;
}
