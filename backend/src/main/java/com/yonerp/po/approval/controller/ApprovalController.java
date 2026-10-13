package com.yonerp.po.approval.controller;

import com.yonerp.po.approval.service.ApprovalService;
import com.yonerp.po.approval.vo.ApprovalTodoVO;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.order.dto.ActionRequest;
import com.yonerp.po.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批接口：待办列表、审批通过、审批驳回
 */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/todo")
    @RequireRole({RoleCode.PURCHASE_MANAGER, RoleCode.DIRECTOR})
    public Result<PageResult<ApprovalTodoVO>> todo(@RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") long pageNo,
                                                   @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(approvalService.todo(keyword, pageNo, pageSize));
    }

    @GetMapping("/todo-count")
    public Result<Long> todoCount() {
        return Result.ok(approvalService.todoCount());
    }

    @PostMapping("/{taskId}/approve")
    @RequireRole({RoleCode.PURCHASE_MANAGER, RoleCode.DIRECTOR})
    public Result<Void> approve(@PathVariable Long taskId,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @RequestBody(required = false) ActionRequest request) {
        ActionRequest command = request == null ? new ActionRequest() : request;
        if (command.getIdempotencyKey() == null) {
            command.setIdempotencyKey(idempotencyKey);
        }
        approvalService.approve(taskId, command);
        return Result.ok();
    }

    @PostMapping("/{taskId}/reject")
    @RequireRole({RoleCode.PURCHASE_MANAGER, RoleCode.DIRECTOR})
    public Result<Void> reject(@PathVariable Long taskId,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                               @RequestBody(required = false) ActionRequest request) {
        ActionRequest command = request == null ? new ActionRequest() : request;
        if (command.getIdempotencyKey() == null) {
            command.setIdempotencyKey(idempotencyKey);
        }
        approvalService.reject(taskId, command);
        return Result.ok();
    }
}
