package com.yonerp.po.ap.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 差异处理命令
 */
@Data
public class ResolveCmd {

    /**
     * 处理方式：ADD_RECEIPT(补收货) / REDO_INVOICE(退票重开) / ADJUST_PRICE(调价)
     * / ALLOW_LOSS(合理损耗放行) / FORCE_MATCH(领导审批强制匹配)
     */
    @NotBlank(message = "处理方式不能为空")
    private String resolution;

    /**
     * 处理意见（必填）：差异原因说明与处理依据，写入审计时间线
     */
    @NotBlank(message = "请填写处理意见（差异原因与处理依据）")
    private String remark;
}
