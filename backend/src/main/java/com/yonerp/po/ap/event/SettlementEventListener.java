package com.yonerp.po.ap.event;

import com.yonerp.po.ap.service.AccrualService;
import com.yonerp.po.ap.service.MatchEngine;
import com.yonerp.po.order.event.OrderUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 结算事件监听器（用友式自动结算）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventListener {

    private final MatchEngine matchEngine;

    private final AccrualService accrualService;

    /**
     * 收货确认后异步触发自动结算
     */
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReceiptConfirmed(ReceiptConfirmedEvent event) {
        try {
            log.info("收到收货确认事件，触发自动结算：orderId={}, receiptId={}",
                event.getOrderId(), event.getReceiptId());
            matchEngine.matchByOrder(event.getOrderId());
        } catch (Exception e) {
            log.error("收货确认自动结算失败：orderId={}", event.getOrderId(), e);
        }
    }

    /**
     * 发票校验后异步触发自动结算 + 暂估回冲
     */
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceChecked(InvoiceCheckedEvent event) {
        try {
            log.info("收到发票校验事件，触发自动结算：orderId={}, invoiceId={}",
                event.getOrderId(), event.getInvoiceId());
            matchEngine.matchByOrder(event.getOrderId());
            accrualService.reverseByInvoice(event.getInvoiceId());
        } catch (Exception e) {
            log.error("发票校验自动结算失败：orderId={}", event.getOrderId(), e);
        }
    }

    /**
     * 订单调价后异步触发自动结算（支撑差异处理「调价」动作闭环）
     */
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(OrderUpdatedEvent event) {
        try {
            log.info("收到订单更新事件，触发自动结算：orderId={}", event.getOrderId());
            matchEngine.matchByOrder(event.getOrderId());
        } catch (Exception e) {
            log.error("订单更新自动结算失败：orderId={}", event.getOrderId(), e);
        }
    }
}
