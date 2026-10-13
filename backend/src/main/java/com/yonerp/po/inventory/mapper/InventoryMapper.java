package com.yonerp.po.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.inventory.entity.PoInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 库存 Mapper
 */
@Mapper
public interface InventoryMapper extends BaseMapper<PoInventory> {

    /**
     * CAS 更新库存（乐观锁）
     *
     * @return 影响行数，0 表示版本冲突需重试
     */
    @Update("UPDATE po_inventory SET quantity = quantity + #{delta}, version = version + 1, " +
            "last_update_time = NOW() WHERE id = #{id} AND version = #{version} AND deleted = 0")
    int casUpdateQuantity(@Param("id") Long id, @Param("delta") BigDecimal delta, @Param("version") Integer version);
}
