package com.yonerp.po.ap.service;

import com.yonerp.po.ap.dto.AccrualQuery;
import com.yonerp.po.ap.entity.PoAccrual;
import com.yonerp.po.ap.vo.AccrualVO;
import com.yonerp.po.common.PageResult;

import java.util.List;

/**
 * 暂估应付服务
 */
public interface AccrualService {

    /**
     * 收货确认后按物料分行生成暂估（货到票未到）
     */
    List<PoAccrual> accrueByReceipt(Long receiptId);

    /**
     * 发票校验后回冲暂估（单到回冲），差额按暂估金额占比分摊到行
     */
    void reverseByInvoice(Long invoiceId);

    /**
     * 月初批量回冲（月初回冲）
     */
    List<PoAccrual> monthlyReverse(String period);

    /**
     * 月末批量暂估：扫当期货到票未到的收货单，按物料分行生成暂估
     */
    List<PoAccrual> monthlyAccrue(String period);

    /**
     * 暂估列表分页查询
     */
    PageResult<AccrualVO> page(AccrualQuery query);

    /**
     * 生成暂估单号
     */
    String generateAccrualNo();
}
