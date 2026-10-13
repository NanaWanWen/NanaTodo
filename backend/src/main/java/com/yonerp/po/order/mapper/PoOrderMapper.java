package com.yonerp.po.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.order.entity.PoOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购订单 Mapper
 */
@Mapper
public interface PoOrderMapper extends BaseMapper<PoOrder> {
}
