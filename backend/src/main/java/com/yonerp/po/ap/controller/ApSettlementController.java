package com.yonerp.po.ap.controller;

import com.yonerp.po.ap.dto.ApQuery;
import com.yonerp.po.ap.dto.ForceSettleCmd;
import com.yonerp.po.ap.dto.ManualSettleCmd;
import com.yonerp.po.ap.dto.ResolveCmd;
import com.yonerp.po.ap.entity.PoApSettlement;
import com.yonerp.po.ap.service.ApSettlementService;
import com.yonerp.po.ap.vo.ApSettlementListVO;
import com.yonerp.po.ap.vo.SupplierBalanceVO;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 应付对账 / 三单匹配接口（用友式）
 */
@RestController
@RequestMapping("/api/ap")
@RequiredArgsConstructor
public class ApSettlementController {

    private final ApSettlementService settlementService;

    @GetMapping("/settlements")
    public Result<PageResult<ApSettlementListVO>> settlements(@RequestParam(required = false) Long orderId,
                                                              @RequestParam(required = false) String matchStatus,
                                                              @RequestParam(required = false) String matchType,
                                                              @RequestParam(required = false) String diffType,
                                                              @RequestParam(required = false) Long supplierId,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(defaultValue = "1") long pageNo,
                                                              @RequestParam(defaultValue = "20") long pageSize) {
        ApQuery query = new ApQuery();
        query.setOrderId(orderId);
        query.setMatchStatus(matchStatus);
        query.setMatchType(matchType);
        query.setDiffType(diffType);
        query.setSupplierId(supplierId);
        query.setKeyword(keyword);
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

    /**
     * 批量自动结算
     */
    @PostMapping("/settlements/auto-settle")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<List<PoApSettlement>> autoSettle(@RequestBody List<Long> orderIds) {
        return Result.ok(settlementService.autoSettle(orderIds));
    }

    /**
     * 手工结算
     */
    @PostMapping("/settlements/manual-settle")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER, RoleCode.BUYER})
    public Result<PoApSettlement> manualSettle(@RequestBody ManualSettleCmd cmd) {
        return Result.ok(settlementService.manualSettle(cmd));
    }

    /**
     * 强制匹配（需审批）
     */
    @PostMapping("/settlements/{settlementId}/force-settle")
    @RequireRole({RoleCode.ADMIN, RoleCode.DIRECTOR})
    public Result<Void> forceSettle(@PathVariable Long settlementId, @RequestBody ForceSettleCmd cmd) {
        settlementService.forceSettle(settlementId, cmd);
        return Result.ok();
    }

    /**
     * 差异处理
     */
    @PostMapping("/settlements/{settlementId}/resolve-diff")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<Void> resolveDiff(@PathVariable Long settlementId, @Valid @RequestBody ResolveCmd cmd) {
        settlementService.resolveDiff(settlementId, cmd);
        return Result.ok();
    }

    @GetMapping("/balances")
    public Result<List<SupplierBalanceVO>> supplierBalances() {
        return Result.ok(settlementService.supplierBalances());
    }
}
