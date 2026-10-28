package com.yonerp.po.ap.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 发票校验事件
 */
@Getter
public class InvoiceCheckedEvent extends ApplicationEvent {

    private final Long invoiceId;

    private final Long orderId;

    public InvoiceCheckedEvent(Object source, Long invoiceId, Long orderId) {
        super(source);
        this.invoiceId = invoiceId;
        this.orderId = orderId;
    }
}
