package com.yonerp.po.ap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.ap.dto.ApQuery;
import com.yonerp.po.ap.entity.PoApSettlement;
import com.yonerp.po.ap.mapper.ApSettlementMapper;
import com.yonerp.po.ap.service.ApSettlementService;
import com.yonerp.po.ap.vo.ApSettlementListVO;
import com.yonerp.po.ap.vo.SupplierBalanceVO;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.invoice.entity.PoInvoice;
import com.yonerp.po.invoice.mapper.InvoiceMapper;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.payment.entity.PoPayment;
import com.yonerp.po.payment.mapper.PaymentMapper;
import com.yonerp.po.receipt.entity.PoReceipt;
import com.yonerp.po.receipt.mapper.ReceiptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 应付对账 / 三单匹配服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApSettlementServiceImpl implements ApSettlementService {

    /** 默认容差：1% */
    private static final BigDecimal TOLERANCE_RATIO = new BigDecimal("0.01");

    private final ApSettlementMapper settlementMapper;
    private final PoOrderMapper orderMapper;
    private final ReceiptMapper receiptMapper;
    private final InvoiceMapper invoiceMapper;
    private final PaymentMapper paymentMapper;
    private final SupplierMapper supplierMapper;

    @Override
    public PageResult<ApSettlementListVO> page(ApQuery query) {
        LambdaQueryWrapper<PoApSettlement> wrapper = new LambdaQueryWrapper<>();
        if (query.getOrderId() != null) {
            wrapper.eq(PoApSettlement::getOrderId, query.getOrderId());
        }
        if (query.getMatchStatus() != null && !query.getMatchStatus().isBlank()) {
            wrapper.eq(PoApSettlement::getMatchStatus, query.getMatchStatus());
        }
        wrapper.orderByDesc(PoApSettlement::getUpdateTime);

        var page = settlementMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page, this::toListVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void match(Long orderId) {
        PoOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("三单匹配跳过：订单 {} 不存在", orderId);
            return;
        }
        PoReceipt receipt = receiptMapper.selectOne(new LambdaQueryWrapper<PoReceipt>().eq(PoReceipt::getOrderId, orderId));
        PoInvoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<PoInvoice>().eq(PoInvoice::getOrderId, orderId));
        if (receipt == null || invoice == null) {
            log.info("三单匹配跳过：订单 {} 缺少收货单或发票", orderId);
            return;
        }

        BigDecimal receiptAmount = receipt.getTotalAmount();
        BigDecimal invoiceAmount = invoice.getTotalAmount();
        BigDecimal orderAmount = order.getTotalAmount();
        BigDecimal diff = invoiceAmount.subtract(receiptAmount).abs();
        BigDecimal tolerance = receiptAmount.multiply(TOLERANCE_RATIO).setScale(2, RoundingMode.HALF_UP);
        String status = diff.compareTo(tolerance) <= 0 ? "MATCHED" : "PARTIAL";

        PoApSettlement exist = settlementMapper.selectOne(new LambdaQueryWrapper<PoApSettlement>().eq(PoApSettlement::getOrderId, orderId));
        if (exist == null) {
            exist = new PoApSettlement();
            exist.setOrderId(orderId);
        }
        exist.setReceiptId(receipt.getId());
        exist.setInvoiceId(invoice.getId());
        exist.setOrderAmount(orderAmount);
        exist.setReceiptAmount(receiptAmount);
        exist.setInvoiceAmount(invoiceAmount);
        exist.setDiffAmount(diff);
        exist.setTolerance(tolerance);
        exist.setMatchStatus(status);
        if (exist.getId() == null) {
            settlementMapper.insert(exist);
        } else {
            settlementMapper.updateById(exist);
        }
        log.info("三单匹配完成：orderId={}, status={}, diff={}", orderId, status, diff);
    }

    @Override
    public List<SupplierBalanceVO> supplierBalances() {
        // 供应商在现有模型中存于 base_supplier 表
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getDeleted, 0));
        List<SupplierBalanceVO> result = new java.util.ArrayList<>();
        for (Supplier supplier : suppliers) {
            SupplierBalanceVO vo = new SupplierBalanceVO();
            vo.setSupplierId(supplier.getId());
            vo.setSupplierName(supplier.getSupplierName());

            // 未付款发票金额合计
            List<PoInvoice> unpaid = invoiceMapper.selectList(new LambdaQueryWrapper<PoInvoice>()
                    .eq(PoInvoice::getSupplierId, supplier.getId())
                    .in(PoInvoice::getInvoiceStatus, "UNCHECKED", "CHECKED"));
            BigDecimal unpaidAmount = BigDecimal.ZERO;
            for (PoInvoice invoice : unpaid) {
                unpaidAmount = unpaidAmount.add(invoice.getTotalAmount());
            }
            vo.setUnpaidAmount(unpaidAmount);
            vo.setUnpaidInvoiceCount(unpaid.size());

            // 已付款金额合计
            List<PoPayment> paid = paymentMapper.selectList(new LambdaQueryWrapper<PoPayment>()
                    .eq(PoPayment::getSupplierId, supplier.getId())
                    .eq(PoPayment::getStatus, "PAID"));
            BigDecimal paidAmount = BigDecimal.ZERO;
            for (PoPayment payment : paid) {
                paidAmount = paidAmount.add(payment.getPaymentAmount());
            }
            vo.setPaidAmount(paidAmount);
            vo.setBalance(unpaidAmount.subtract(paidAmount));

            result.add(vo);
        }
        return result;
    }

    // ==================== 私有方法 ====================

    private ApSettlementListVO toListVO(PoApSettlement s) {
        ApSettlementListVO vo = new ApSettlementListVO();
        vo.setId(s.getId());
        vo.setOrderId(s.getOrderId());
        vo.setReceiptId(s.getReceiptId());
        vo.setInvoiceId(s.getInvoiceId());
        vo.setOrderAmount(s.getOrderAmount());
        vo.setReceiptAmount(s.getReceiptAmount());
        vo.setInvoiceAmount(s.getInvoiceAmount());
        vo.setDiffAmount(s.getDiffAmount());
        vo.setMatchStatus(s.getMatchStatus());
        vo.setTolerance(s.getTolerance());
        vo.setRemark(s.getRemark());
        vo.setUpdateTime(s.getUpdateTime());

        PoOrder order = s.getOrderId() != null ? orderMapper.selectById(s.getOrderId()) : null;
        vo.setOrderNo(order == null ? null : order.getOrderNo());

        PoReceipt receipt = s.getReceiptId() != null ? receiptMapper.selectById(s.getReceiptId()) : null;
        vo.setReceiptNo(receipt == null ? null : receipt.getReceiptNo());

        PoInvoice invoice = s.getInvoiceId() != null ? invoiceMapper.selectById(s.getInvoiceId()) : null;
        vo.setInvoiceNo(invoice == null ? null : invoice.getInvoiceNo());

        return vo;
    }
}
