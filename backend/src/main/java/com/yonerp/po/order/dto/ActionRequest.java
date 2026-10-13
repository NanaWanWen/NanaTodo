package com.yonerp.po.order.dto;

import lombok.Data;

/**
 * 状态动作请求（提交/撤回/取消/审批）
 */
@Data
public class ActionRequest {

    /** 备注或意见（驳回与取消必填） */
    private String remark;

    /** 乐观锁版本号 */
    private Integer version;

    /** 幂等键（前端生成 UUID，亦可放请求头 Idempotency-Key） */
    private String idempotencyKey;
}
