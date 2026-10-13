package com.yonerp.po.receipt.controller;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.receipt.dto.ReceiptCreateCmd;
import com.yonerp.po.receipt.dto.ReceiptQuery;
import com.yonerp.po.receipt.service.ReceiptService;
import com.yonerp.po.receipt.vo.ReceiptDetailVO;
import com.yonerp.po.receipt.vo.ReceiptListVO;
import com.yonerp.po.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 采购收货单接口
 */
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    public Result<PageResult<ReceiptListVO>> page(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long supplierId,
                                                  @RequestParam(required = false) Long orderId,
                                                  @RequestParam(defaultValue = "1") long pageNo,
                                                  @RequestParam(defaultValue = "20") long pageSize) {
        ReceiptQuery query = new ReceiptQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setSupplierId(supplierId);
        query.setOrderId(orderId);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.ok(receiptService.page(query));
    }

    @GetMapping("/{id}")
    public Result<ReceiptDetailVO> detail(@PathVariable Long id) {
        return Result.ok(receiptService.detail(id));
    }

    @PostMapping
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Long> create(@Valid @RequestBody ReceiptCreateCmd cmd) {
        return Result.ok(receiptService.create(cmd));
    }

    @PostMapping("/{id}/actions/confirm")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> confirm(@PathVariable Long id) {
        receiptService.confirm(id);
        return Result.ok();
    }

    @PostMapping("/{id}/actions/cancel")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> cancel(@PathVariable Long id) {
        receiptService.cancel(id);
        return Result.ok();
    }
}
