package com.yonerp.po.receipt.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收货单详情 VO
 */
@Data
public class ReceiptDetailVO {

    private Long id;

    private String receiptNo;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private LocalDate receiptDate;

    private BigDecimal totalQty;

    private BigDecimal totalAmount;

    private String status;

    private String remark;

    private LocalDateTime createTime;

    private List<ItemVO> items;

    @Data
    public static class ItemVO {

        private Long id;

        private Long orderItemId;

        private Long materialId;

        private String materialName;

        private String spec;

        private String unit;

        private BigDecimal orderedQty;

        private BigDecimal receivedQty;

        private BigDecimal unitPrice;

        private BigDecimal taxRate;

        private BigDecimal taxAmount;

        private BigDecimal amount;

        private String remark;
    }
}
