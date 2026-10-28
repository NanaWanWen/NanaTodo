package com.yonerp.po.ap.controller;

import com.yonerp.po.ap.dto.AccrualQuery;
import com.yonerp.po.ap.entity.PoAccrual;
import com.yonerp.po.ap.service.AccrualService;
import com.yonerp.po.ap.vo.AccrualVO;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 暂估应付接口（用友式）
 */
@RestController
@RequestMapping("/api/ap/accruals")
@RequiredArgsConstructor
public class AccrualController {

    private final AccrualService accrualService;

    /**
     * 暂估列表（分页 + 期间/状态/供应商/关键字筛选）
     */
    @GetMapping
    public Result<PageResult<AccrualVO>> list(@RequestParam(required = false) String period,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long supplierId,
                                              @RequestParam(required = false) Long orderId,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") long pageNo,
                                              @RequestParam(defaultValue = "20") long pageSize) {
        AccrualQuery query = new AccrualQuery();
        query.setPeriod(period);
        query.setStatus(status);
        query.setSupplierId(supplierId);
        query.setOrderId(orderId);
        query.setKeyword(keyword);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.ok(accrualService.page(query));
    }

    /**
     * 按收货单生成暂估（货到票未到，按物料分行）
     */
    @PostMapping("/by-receipt/{receiptId}")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<List<PoAccrual>> accrueByReceipt(@PathVariable Long receiptId) {
        return Result.ok(accrualService.accrueByReceipt(receiptId));
    }

    /**
     * 按发票回冲暂估（单到回冲）
     */
    @PostMapping("/reverse-by-invoice/{invoiceId}")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<Void> reverseByInvoice(@PathVariable Long invoiceId) {
        accrualService.reverseByInvoice(invoiceId);
        return Result.ok();
    }

    /**
     * 月末批量暂估
     */
    @PostMapping("/monthly-accrue")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<List<PoAccrual>> monthlyAccrue(@RequestParam String period) {
        return Result.ok(accrualService.monthlyAccrue(period));
    }

    /**
     * 月初批量回冲
     */
    @PostMapping("/monthly-reverse")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<List<PoAccrual>> monthlyReverse(@RequestParam String period) {
        return Result.ok(accrualService.monthlyReverse(period));
    }
}
