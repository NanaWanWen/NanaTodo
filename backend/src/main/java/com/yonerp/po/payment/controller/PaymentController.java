package com.yonerp.po.payment.controller;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.payment.dto.PaymentCreateCmd;
import com.yonerp.po.payment.dto.PaymentQuery;
import com.yonerp.po.payment.service.PaymentService;
import com.yonerp.po.payment.vo.PaymentListVO;
import com.yonerp.po.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 付款单接口
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public Result<PageResult<PaymentListVO>> page(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long supplierId,
                                                   @RequestParam(required = false) Long orderId,
                                                   @RequestParam(defaultValue = "1") long pageNo,
                                                   @RequestParam(defaultValue = "20") long pageSize) {
        PaymentQuery query = new PaymentQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setSupplierId(supplierId);
        query.setOrderId(orderId);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.ok(paymentService.page(query));
    }

    @PostMapping
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Long> create(@Valid @RequestBody PaymentCreateCmd cmd) {
        return Result.ok(paymentService.create(cmd));
    }

    @PostMapping("/{id}/actions/confirm")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> confirm(@PathVariable Long id) {
        paymentService.confirm(id);
        return Result.ok();
    }

    @PostMapping("/{id}/actions/reject")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> reject(@PathVariable Long id) {
        paymentService.reject(id);
        return Result.ok();
    }
}
