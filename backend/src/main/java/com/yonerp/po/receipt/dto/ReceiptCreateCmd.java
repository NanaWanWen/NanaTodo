package com.yonerp.po.receipt.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建收货单请求
 */
@Data
public class ReceiptCreateCmd {

    /** 订单ID */
    private Long orderId;

    /** 收货日期 */
    private LocalDate receiptDate;

    /** 备注 */
    private String remark;

    /** 收货明细（每行对应一个订单明细行） */
    private List<ItemCmd> items;

    @Data
    public static class ItemCmd {

        /** 订单明细ID */
        private Long orderItemId;

        /** 实收数量 */
        private BigDecimal receivedQty;

        /** 备注 */
        private String remark;
    }
}
