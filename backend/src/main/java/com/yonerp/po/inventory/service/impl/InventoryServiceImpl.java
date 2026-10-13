package com.yonerp.po.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yonerp.po.base.entity.Material;
import com.yonerp.po.base.mapper.MaterialMapper;
import com.yonerp.po.common.BizException;
import com.yonerp.po.inventory.entity.PoInventory;
import com.yonerp.po.inventory.mapper.InventoryMapper;
import com.yonerp.po.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存服务实现：CAS 乐观锁更新，冲突重试 3 次
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;

    private static final int MAX_RETRIES = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addStock(Long materialId, BigDecimal delta) {
        updateStock(materialId, delta, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void subtractStock(Long materialId, BigDecimal delta) {
        updateStock(materialId, delta, false);
    }

    @Override
    public List<PoInventory> lowStock() {
        // 跨列比较（quantity < safety_stock）用 QueryWrapper + 列名字符串
        QueryWrapper<PoInventory> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .and(w -> w.lt("quantity", "safety_stock")
                        .or()
                        .le("quantity", "safety_stock"));
        return inventoryMapper.selectList(wrapper);
    }

    @Override
    public List<PoInventory> listAll() {
        return inventoryMapper.selectList(new LambdaQueryWrapper<PoInventory>()
                .eq(PoInventory::getDeleted, 0)
                .orderByDesc(PoInventory::getQuantity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSafetyStock(Long inventoryId, BigDecimal safetyStock) {
        PoInventory inv = inventoryMapper.selectById(inventoryId);
        if (inv == null) {
            throw BizException.notFound("库存记录不存在");
        }
        inv.setSafetyStock(safetyStock);
        inventoryMapper.updateById(inv);
        log.info("更新安全库存：inventoryId={}, materialId={}, safetyStock={}",
                inventoryId, inv.getMaterialId(), safetyStock);
    }

    // ==================== 私有方法 ====================

    private void updateStock(Long materialId, BigDecimal delta, boolean isAdd) {
        // 获取或创建库存记录
        PoInventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<PoInventory>()
                        .eq(PoInventory::getMaterialId, materialId)
                        .eq(PoInventory::getWarehouse, "主仓库")
                        .eq(PoInventory::getDeleted, 0));
        if (inv == null) {
            inv = createInventory(materialId);
        }

        // 出库校验
        if (!isAdd) {
            BigDecimal result = inv.getQuantity().subtract(delta);
            if (result.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(2005, "库存不足：物料 " + inv.getMaterialName() + " 当前库存 " + inv.getQuantity() + "，需要扣减 " + delta);
            }
        }

        // CAS 更新，冲突重试
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            int rows = inventoryMapper.casUpdateQuantity(inv.getId(), delta, inv.getVersion());
            if (rows > 0) {
                log.info("库存更新成功：materialId={}, delta={}, oldQty={}, newQty={}, version={}",
                        materialId, delta, inv.getQuantity(),
                        inv.getQuantity().add(delta), inv.getVersion() + 1);
                return;
            }
            // 版本冲突，重新查询
            PoInventory fresh = inventoryMapper.selectById(inv.getId());
            if (fresh == null) {
                throw new BizException(4002, "库存记录不存在");
            }
            inv = fresh;
        }
        throw new BizException(4002, "库存并发更新冲突，请重试");
    }

    private PoInventory createInventory(Long materialId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            throw BizException.notFound("物料不存在");
        }
        PoInventory inv = new PoInventory();
        inv.setMaterialId(materialId);
        inv.setMaterialCode(material.getMaterialCode());
        inv.setMaterialName(material.getMaterialName());
        inv.setWarehouse("主仓库");
        inv.setQuantity(BigDecimal.ZERO);
        inv.setSafetyStock(BigDecimal.ZERO);
        inv.setVersion(0);
        inventoryMapper.insert(inv);
        return inv;
    }
}
