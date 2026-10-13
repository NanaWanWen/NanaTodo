package com.yonerp.po.approval.controller;

import com.yonerp.po.approval.service.ApprovalService;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.Result;
import com.yonerp.po.order.dto.ActionRequest;
import com.yonerp.po.order.vo.OrderDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单状态动作接口：submit / withdraw / cancel
 *
 * <p>状态变更只能走这里，禁止通过更新接口直接改状态。</p>
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderActionController {

    private final ApprovalService approvalService;

    @PostMapping("/{id}/actions/{action}")
    public Result<OrderDetailVO> action(@PathVariable Long id,
                                        @PathVariable String action,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                        @RequestBody(required = false) ActionRequest request) {
        ActionRequest command = request == null ? new ActionRequest() : request;
        if (command.getIdempotencyKey() == null) {
            command.setIdempotencyKey(idempotencyKey);
        }
        switch (action) {
            case "submit" -> approvalService.submit(id, command);
            case "withdraw" -> approvalService.withdraw(id, command);
            case "cancel" -> approvalService.cancel(id, command);
            default -> throw BizException.param("不支持的动作：" + action);
        }
        return Result.ok(approvalService.detailAfterAction(id));
    }
}
