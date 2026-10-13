package com.yonerp.po.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 缓存清理器：确保在事务提交后再删除缓存，避免回滚造成缓存与数据库不一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictor {

    private final CacheService cacheService;

    /**
     * 订单与审批写操作后清理：报表缓存 + 待办角标
     */
    public void evictOrderRelated() {
        runAfterCommit(() -> {
            cacheService.deleteByPrefix(RedisKeys.REPORT);
            cacheService.deleteByPrefix(RedisKeys.BADGE);
        });
    }

    /**
     * 基础数据写操作后清理
     */
    public void evictBaseData(String... keys) {
        runAfterCommit(() -> cacheService.delete(keys));
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
