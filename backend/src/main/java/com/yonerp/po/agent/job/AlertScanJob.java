package com.yonerp.po.agent.job;

import com.yonerp.po.agent.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 预警定时扫描任务
 * <p>
 * 启动后 1 分钟首次执行，之后按 {@code alert.scan.interval-minutes} 间隔扫描。
 * 扫描逻辑委托给 {@link AlertRuleService} 按规则动态驱动，
 * 内置评估器已覆盖：逾期订单、低库存、价格异常。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScanJob {

    private final AlertRuleService alertRuleService;

    @Value("${yonerp.agent.alert-scan-interval-minutes:5}")
    private int scanIntervalMinutes;

    /**
     * 定时扫描预警（启动后 1 分钟首次执行，之后每 N 分钟一次）
     *
     * <p>注意：`${...}` 占位符先于 SpEL 求值，不能把配置键直接写在 SpEL 里，
     * 否则 SpEL 会把 `yonerp` 当作上下文属性导致启动失败。</p>
     */
    @Scheduled(fixedRateString = "#{T(java.time.Duration).ofMinutes(${yonerp.agent.alert-scan-interval-minutes:5}).toMillis()}", initialDelay = 60_000)
    public void scanAlerts() {
        scan();
    }

    /**
     * 立即执行一次扫描（供手动触发使用），返回本次新生成的预警数量
     */
    public int scan() {
        log.info("AlertScanJob: starting scan (interval={}min)", scanIntervalMinutes);
        int count = 0;
        try {
            count = alertRuleService.scanAll();
        } catch (Exception e) {
            log.error("AlertScanJob: scan failed", e);
        }
        log.info("AlertScanJob: scan complete, generated {} alerts", count);
        return count;
    }
}
