package com.yonerp.po.inventory.service;

import com.yonerp.po.inventory.entity.PoInventory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存服务
 */
public interface InventoryService {

    /**
     * 增加库存（收货入库），基于物料 CAS 更新
     *
     * @param materialId 物料ID
     * @param delta      数量增量（正数）
     */
    void addStock(Long materialId, BigDecimal delta);

    /**
     * 减少库存（发货出库）
     *
     * @param materialId 物料ID
     * @param delta      数量减量（正数）
     */
    void subtractStock(Long materialId, BigDecimal delta);

    /**
     * 低于安全库存的物料列表
     */
    List<PoInventory> lowStock();

    /**
     * 全量库存列表
     */
    List<PoInventory> listAll();

    /**
     * 更新物料安全库存
     *
     * @param inventoryId  库存记录ID
     * @param safetyStock  安全库存数量
     */
    void updateSafetyStock(Long inventoryId, java.math.BigDecimal safetyStock);
}
