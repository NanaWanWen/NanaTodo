package com.yonerp.po.invoice.service;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.invoice.dto.InvoiceCreateCmd;
import com.yonerp.po.invoice.dto.InvoiceQuery;
import com.yonerp.po.invoice.vo.InvoiceListVO;

/**
 * 采购发票服务
 */
public interface InvoiceService {

    /**
     * 分页列表
     */
    PageResult<InvoiceListVO> page(InvoiceQuery query);

    /**
     * 创建发票（关联收货单，金额基于收货单计算）
     *
     * @return 发票 ID
     */
    Long create(InvoiceCreateCmd cmd);

    /**
     * 核对发票（UNCHECKED → CHECKED）
     */
    void check(Long id);

    /**
     * 撤销核对（CHECKED → UNCHECKED）
     */
    void uncheck(Long id);
}
