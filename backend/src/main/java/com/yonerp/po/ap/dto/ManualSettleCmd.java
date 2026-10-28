package com.yonerp.po.ap.dto;

import lombok.Data;

import java.util.List;

/**
 * 手工结算命令
 */
@Data
public class ManualSettleCmd {

    /** 订单ID */
    private Long orderId;

    /** 收货单ID */
    private Long receiptId;

    /** 发票ID */
    private Long invoiceId;

    /** 备注 */
    private String remark;
}
