package com.yonerp.po.importx.config;

import com.yonerp.po.importx.core.ImportBizHandler;
import com.yonerp.po.importx.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * 导入处理器注册器：应用启动时自动把所有 ImportBizHandler 注册到 ImportService。
 */
@Configuration
@RequiredArgsConstructor
public class ImportHandlerRegistrar {

    private final ImportService importService;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, ImportBizHandler> beans = applicationContext.getBeansOfType(ImportBizHandler.class);
        beans.values().forEach(importService::registerHandler);
    }
}
