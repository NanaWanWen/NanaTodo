package com.yonerp.po.receipt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.receipt.entity.PoReceiptItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货单明细 Mapper
 */
@Mapper
public interface ReceiptItemMapper extends BaseMapper<PoReceiptItem> {
}
