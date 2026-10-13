package com.yonerp.po.receipt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.receipt.entity.PoReceipt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货单 Mapper
 */
@Mapper
public interface ReceiptMapper extends BaseMapper<PoReceipt> {
}
