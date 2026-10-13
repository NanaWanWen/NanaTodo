package com.yonerp.po.ap.controller;

import com.yonerp.po.ap.dto.ApQuery;
import com.yonerp.po.ap.service.ApSettlementService;
import com.yonerp.po.ap.vo.ApSettlementListVO;
import com.yonerp.po.ap.vo.SupplierBalanceVO;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应付对账 / 三单匹配接口
 */
@RestController
@RequestMapping("/api/ap")
@RequiredArgsConstructor
public class ApSettlementController {

    private final ApSettlementService settlementService;

    @GetMapping("/settlements")
    public Result<PageResult<ApSettlementListVO>> settlements(@RequestParam(required = false) Long orderId,
                                                              @RequestParam(required = false) String matchStatus,
                                                              @RequestParam(defaultValue = "1") long pageNo,
                                                              @RequestParam(defaultValue = "20") long pageSize) {
        ApQuery query = new ApQuery();
        query.setOrderId(orderId);
        query.setMatchStatus(matchStatus);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.ok(settlementService.page(query));
    }

    @PostMapping("/settlements/{orderId}/match")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER, RoleCode.BUYER})
    public Result<Void> match(@PathVariable Long orderId) {
        settlementService.match(orderId);
        return Result.ok();
    }

    @GetMapping("/balances")
    public Result<List<SupplierBalanceVO>> supplierBalances() {
        return Result.ok(settlementService.supplierBalances());
    }
}
