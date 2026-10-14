package com.yonerp.po.tplus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** T+ Cloud 只读同步配置。密钥仅从环境变量注入。 */
@Data
@Component
@ConfigurationProperties(prefix = "yonerp.tplus")
public class TplusProperties {

    private boolean enabled = false;
    private boolean dryRun = true;
    private String accountId = "1243337607745442";
    private String appKey = "";
    private String appSecret = "";
    private String tokenUrl = "https://openapi.chanjet.com/auth/v2/getToken";
    private String baseUrl = "https://openapi.chanjet.com";
    private String supplierPath = "/tplus/api/v2/partner/QueryPage";
    private String materialPath = "";
    private String purchaseOrderPath = "";
    private String inventoryPath = "";
    private int pageSize = 100;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 30;
    private int maxRetries = 2;
    private int syncIntervalMinutes = 10;

    public boolean hasCredentials() {
        return !appKey.isBlank() && !appSecret.isBlank();
    }

    public boolean hasAccount() {
        return accountId != null && !accountId.isBlank();
    }
}
