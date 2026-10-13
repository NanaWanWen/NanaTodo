package com.yonerp.po.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.invoice.entity.PoInvoice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购发票 Mapper
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<PoInvoice> {
}
