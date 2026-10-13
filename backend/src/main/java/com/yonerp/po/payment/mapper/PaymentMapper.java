package com.yonerp.po.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.payment.entity.PoPayment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 付款单 Mapper
 */
@Mapper
public interface PaymentMapper extends BaseMapper<PoPayment> {
}
