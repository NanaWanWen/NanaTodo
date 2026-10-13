package com.yonerp.po.base;

import com.yonerp.po.base.service.impl.DictServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动预热热点字典缓存，避免冷启动缓存击穿
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictWarmUpRunner implements ApplicationRunner {

    private final DictServiceImpl dictService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            dictService.warmUp();
        } catch (Exception e) {
            log.warn("字典缓存预热失败，将在首次访问时懒加载", e);
        }
    }
}
