package com.yonerp.po.ap.dto;

import lombok.Data;

/**
 * 强制匹配命令
 */
@Data
public class ForceSettleCmd {

    /** 强制匹配原因 */
    private String reason;

    /** 处理方式 */
    private String resolution;
}
