package com.yonerp.po.order.event;

import org.springframework.context.ApplicationEvent;

/**
 * 订单更新事件：订单调价后触发三单匹配重算（支撑差异处理「调价」动作闭环）
 */
public class OrderUpdatedEvent extends ApplicationEvent {

    private final Long orderId;

    public OrderUpdatedEvent(Object source, Long orderId) {
        super(source);
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
