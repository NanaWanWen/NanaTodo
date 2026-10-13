package com.yonerp.po.invoice.controller;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.invoice.dto.InvoiceCreateCmd;
import com.yonerp.po.invoice.dto.InvoiceQuery;
import com.yonerp.po.invoice.service.InvoiceService;
import com.yonerp.po.invoice.vo.InvoiceListVO;
import com.yonerp.po.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 采购发票接口
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public Result<PageResult<InvoiceListVO>> page(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String invoiceStatus,
                                                  @RequestParam(required = false) Long supplierId,
                                                  @RequestParam(required = false) Long orderId,
                                                  @RequestParam(defaultValue = "1") long pageNo,
                                                  @RequestParam(defaultValue = "20") long pageSize) {
        InvoiceQuery query = new InvoiceQuery();
        query.setKeyword(keyword);
        query.setInvoiceStatus(invoiceStatus);
        query.setSupplierId(supplierId);
        query.setOrderId(orderId);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.ok(invoiceService.page(query));
    }

    @PostMapping
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Long> create(@Valid @RequestBody InvoiceCreateCmd cmd) {
        return Result.ok(invoiceService.create(cmd));
    }

    @PostMapping("/{id}/actions/check")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> check(@PathVariable Long id) {
        invoiceService.check(id);
        return Result.ok();
    }

    @PostMapping("/{id}/actions/uncheck")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> uncheck(@PathVariable Long id) {
        invoiceService.uncheck(id);
        return Result.ok();
    }
}
