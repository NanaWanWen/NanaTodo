package com.yonerp.po.invoice.service;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.invoice.dto.InvoiceCreateCmd;
import com.yonerp.po.invoice.dto.InvoiceQuery;
import com.yonerp.po.invoice.dto.InvoiceRedFlushCmd;
import com.yonerp.po.invoice.dto.InvoiceUpdateCmd;
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
     * 创建发票（关联收货单；一收货单可开多张，金额可手工录入或按收货单计算）
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

    /**
     * 修改未核对发票金额（用于退票重开等场景）
     */
    void update(Long id, InvoiceUpdateCmd cmd);

    /**
     * 红冲：开具红字发票抵消原蓝票，原票置 RED_FLUSHED，红票自动核对生效
     *
     * @return 红字发票 ID
     */
    Long redFlush(Long id, InvoiceRedFlushCmd cmd);

    /**
     * 作废：未核对的草稿发票直接作废（VOIDED），不参与匹配
     */
    void voidInvoice(Long id);
}
