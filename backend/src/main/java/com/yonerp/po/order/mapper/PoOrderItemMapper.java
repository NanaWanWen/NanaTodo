package com.yonerp.po.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.order.entity.PoOrderItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 采购订单明细 Mapper
 */
@Mapper
public interface PoOrderItemMapper extends BaseMapper<PoOrderItem> {

    /**
     * 物理删除某订单的全部明细（整行替换场景使用，避免逻辑删除占位导致唯一键冲突）
     */
    @Delete("DELETE FROM po_order_item WHERE order_id = #{orderId}")
    int physicalDeleteByOrderId(@Param("orderId") Long orderId);
}
