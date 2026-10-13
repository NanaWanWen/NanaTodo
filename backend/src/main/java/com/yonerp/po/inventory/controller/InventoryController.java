package com.yonerp.po.inventory.controller;

import com.yonerp.po.common.Result;
import com.yonerp.po.inventory.entity.PoInventory;
import com.yonerp.po.inventory.service.InventoryService;
import com.yonerp.po.security.RequireRole;
import com.yonerp.po.common.enums.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存接口
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public Result<List<PoInventory>> listAll() {
        return Result.ok(inventoryService.listAll());
    }

    @GetMapping("/alerts")
    public Result<List<PoInventory>> lowStock() {
        return Result.ok(inventoryService.lowStock());
    }

    /**
     * 更新物料安全库存
     */
    @PutMapping("/{id}/safety-stock")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> updateSafetyStock(@PathVariable Long id, @RequestParam BigDecimal safetyStock) {
        inventoryService.updateSafetyStock(id, safetyStock);
        return Result.ok();
    }
}
