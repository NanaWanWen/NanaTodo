package com.yonerp.po.agent.job;

import com.yonerp.po.agent.service.AgentService;
import com.yonerp.po.inventory.entity.PoInventory;
import com.yonerp.po.inventory.service.InventoryService;
import com.yonerp.po.order.dto.OrderQuery;
import com.yonerp.po.order.service.OrderService;
import com.yonerp.po.order.vo.OrderListVO;
import com.yonerp.po.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 预警定时扫描任务
 * <p>
 * 每 N 分钟（可配置 yonerp.agent.alert-scan-interval-minutes）扫描一次：
 * 1. 逾期订单：期望到货日期已过且状态为 APPROVING
 * 2. 库存预警：库存量低于安全库存的物料
 * </p>
 * <p>
 * 扫描结果通过 AgentService.generateAlert 生成预警，存入 Redis，
 * 前端通过 /api/agent/alerts 轮询获取。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScanJob {

    private final AgentService agentService;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 定时扫描预警（启动后 1 分钟首次执行，之后每 5 分钟一次，
     * 间隔可通过 yonerp.agent.alert-scan-interval-minutes 配置）
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void scanAlerts() {
        log.info("AlertScanJob: starting scan");
        int count = 0;
        count += scanOverdueOrders();
        count += scanLowStock();
        log.info("AlertScanJob: scan complete, generated {} alerts", count);
    }

    /**
     * 扫描逾期订单：期望到货日期已过且状态仍为 APPROVING
     */
    private int scanOverdueOrders() {
        try {
            OrderQuery query = new OrderQuery();
            query.setStatus("APPROVING");
            query.setEndDate(LocalDate.now().minusDays(1));
            query.setPageNo(1);
            query.setPageSize(100);
            PageResult<OrderListVO> page = orderService.page(query);

            if (page.getRecords().isEmpty()) return 0;

            int generated = 0;
            for (OrderListVO order : page.getRecords()) {
                // 去重：同一订单 24 小时内不重复预警
                String lockKey = "alert:dedup:overdue:" + order.getId();
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", Duration.ofHours(24));
                if (Boolean.TRUE.equals(acquired)) {
                    String detail = String.format(
                            "订单 %s（%s）期望到货日期 %s 已过，当前仍处于审批中状态，请及时跟进。",
                            order.getOrderNo(),
                            order.getSupplierName(),
                            order.getExpectDate()
                    );
                    agentService.generateAlert("OVERDUE_ORDER", order.getId(), order.getOrderNo(), detail);
                    generated++;
                }
            }
            return generated;
        } catch (Exception e) {
            log.error("AlertScanJob: scanOverdueOrders failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 扫描库存预警：当前库存低于安全库存的物料
     */
    private int scanLowStock() {
        try {
            List<PoInventory> lowStock = inventoryService.lowStock();
            if (lowStock.isEmpty()) return 0;

            int generated = 0;
            for (PoInventory inv : lowStock) {
                // 去重：同一物料 12 小时内不重复预警
                String lockKey = "alert:dedup:lowstock:" + inv.getMaterialId();
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", Duration.ofHours(12));
                if (Boolean.TRUE.equals(acquired)) {
                    String detail = String.format(
                            "物料 %s（%s）当前库存 %s，低于安全库存 %s，建议及时补货。",
                            inv.getMaterialCode(),
                            inv.getMaterialName(),
                            inv.getQuantity(),
                            inv.getSafetyStock()
                    );
                    agentService.generateAlert("LOW_STOCK", inv.getMaterialId(),
                            inv.getMaterialCode(), detail);
                    generated++;
                }
            }
            return generated;
        } catch (Exception e) {
            log.error("AlertScanJob: scanLowStock failed: {}", e.getMessage());
            return 0;
        }
    }
}
