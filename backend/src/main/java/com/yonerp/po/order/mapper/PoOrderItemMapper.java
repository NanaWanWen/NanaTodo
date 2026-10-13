package com.yonerp.po.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.order.entity.PoOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购订单明细 Mapper
 */
@Mapper
public interface PoOrderItemMapper extends BaseMapper<PoOrderItem> {
}
