package com.yonerp.po.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建付款单请求
 */
@Data
public class PaymentCreateCmd {

    /** 发票ID */
    private Long invoiceId;

    /** 付款金额（默认等于发票价税合计） */
    private BigDecimal paymentAmount;

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 付款方式：BANK_TRANSFER/ALIPAY/WECHAT/CASH */
    private String paymentMethod;

    /** 备注 */
    private String remark;
}
