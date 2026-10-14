package com.yonerp.po.tplus.job;

import com.yonerp.po.tplus.config.TplusProperties;
import com.yonerp.po.tplus.service.TplusSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** T+ 只读同步调度。当前阶段只执行预览，不写入本地业务表。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TplusSyncJob {

    private final TplusProperties properties;
    private final TplusSyncService syncService;

    @Scheduled(fixedDelayString = "${yonerp.tplus.sync-interval:PT10M}", initialDelayString = "PT1M")
    public void runPreview() {
        if (!properties.isEnabled() || !properties.isDryRun()) {
            return;
        }
        try {
            log.info("T+ read-only preview sync started, accountId={}", properties.getAccountId());
            syncService.preview();
            log.info("T+ read-only preview sync finished");
        } catch (Exception ex) {
            log.warn("T+ read-only preview sync failed; local data remains unchanged: {}", ex.getMessage());
        }
    }
}
