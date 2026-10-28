package com.yonerp.po.ap.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 收货确认事件
 */
@Getter
public class ReceiptConfirmedEvent extends ApplicationEvent {

    private final Long receiptId;

    private final Long orderId;

    public ReceiptConfirmedEvent(Object source, Long receiptId, Long orderId) {
        super(source);
        this.receiptId = receiptId;
        this.orderId = orderId;
    }
}
