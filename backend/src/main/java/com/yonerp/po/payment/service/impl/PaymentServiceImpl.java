package com.yonerp.po.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yonerp.po.ap.entity.PoApSettlement;
import com.yonerp.po.ap.mapper.ApSettlementMapper;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.CacheEvictor;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.MoneyUtils;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.invoice.entity.PoInvoice;
import com.yonerp.po.invoice.mapper.InvoiceMapper;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.payment.dto.PaymentCreateCmd;
import com.yonerp.po.payment.dto.PaymentQuery;
import com.yonerp.po.payment.entity.PoPayment;
import com.yonerp.po.payment.mapper.PaymentMapper;
import com.yonerp.po.payment.service.PaymentService;
import com.yonerp.po.payment.vo.PaymentListVO;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.security.PermissionChecker;
import com.yonerp.po.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 付款单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PaymentMapper paymentMapper;
    private final InvoiceMapper invoiceMapper;
    private final ApSettlementMapper settlementMapper;
    private final PoOrderMapper orderMapper;
    private final SupplierMapper supplierMapper;
    private final CacheService cacheService;
    private final CacheEvictor cacheEvictor;
    private final PermissionChecker permissionChecker;

    @Override
    public PageResult<PaymentListVO> page(PaymentQuery query) {
        LambdaQueryWrapper<PoPayment> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(PoPayment::getPaymentNo, query.getKeyword().trim());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(PoPayment::getStatus, query.getStatus());
        }
        if (query.getSupplierId() != null) {
            wrapper.eq(PoPayment::getSupplierId, query.getSupplierId());
        }
        if (query.getOrderId() != null) {
            wrapper.eq(PoPayment::getOrderId, query.getOrderId());
        }
        wrapper.orderByDesc(PoPayment::getCreateTime);

        IPage<PoPayment> pageResult = paymentMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(pageResult, this::toListVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PaymentCreateCmd cmd) {
        permissionChecker.requireRole(RoleCode.BUYER, RoleCode.ADMIN);
        CurrentUser user = UserContext.required();

        PoInvoice invoice = invoiceMapper.selectById(cmd.getInvoiceId());
        if (invoice == null) {
            throw BizException.notFound("发票不存在");
        }
        if ("PAID".equals(invoice.getInvoiceStatus())) {
            throw BizException.statusNotAllow("发票已付款，不能重复创建付款单");
        }
        if (!"CHECKED".equals(invoice.getInvoiceStatus())) {
            throw BizException.statusNotAllow("仅已核对的发票可以创建付款单");
        }

        // 付款联动：校验三单匹配状态
        PoApSettlement settlement = settlementMapper.selectOne(
            new LambdaQueryWrapper<PoApSettlement>()
                .eq(PoApSettlement::getInvoiceId, cmd.getInvoiceId())
                .eq(PoApSettlement::getDeleted, 0)
        );
        if (settlement == null) {
            throw BizException.statusNotAllow("该发票尚未完成三单匹配，无法创建付款单");
        }
        if (!"MATCHED".equals(settlement.getMatchStatus())) {
            throw BizException.statusNotAllow(
                String.format("三单匹配状态为 %s（非完全匹配），请先处理差异后再付款", settlement.getMatchStatus())
            );
        }

        BigDecimal paymentAmount = cmd.getPaymentAmount() != null ? cmd.getPaymentAmount() : invoice.getTotalAmount();
        if (MoneyUtils.isNegative(paymentAmount)) {
            throw BizException.param("付款金额不能为负数");
        }
        if (paymentAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw BizException.param("付款金额必须大于 0");
        }

        // 累计已付/待付金额校验：同一发票的所有有效付款单（PENDING/PAID）之和不得超过发票价税合计
        BigDecimal paidSum = paymentMapper.selectList(new LambdaQueryWrapper<PoPayment>()
                        .eq(PoPayment::getInvoiceId, cmd.getInvoiceId())
                        .in(PoPayment::getStatus, "PENDING", "PAID"))
                .stream()
                .map(PoPayment::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remain = invoice.getTotalAmount().subtract(paidSum);
        if (paymentAmount.compareTo(remain) > 0) {
            throw BizException.param(String.format(
                    "付款金额 %s 超过发票未付余额 %s（发票金额 %s，已付/待付 %s）",
                    paymentAmount, remain, invoice.getTotalAmount(), paidSum));
        }

        PoPayment payment = new PoPayment();
        payment.setPaymentNo(generatePaymentNo());
        payment.setInvoiceId(cmd.getInvoiceId());
        payment.setOrderId(invoice.getOrderId());
        payment.setSupplierId(invoice.getSupplierId());
        payment.setPaymentDate(cmd.getPaymentDate() != null ? cmd.getPaymentDate() : LocalDate.now());
        payment.setPaymentAmount(paymentAmount);
        payment.setPaymentMethod(cmd.getPaymentMethod() != null ? cmd.getPaymentMethod() : "BANK_TRANSFER");
        payment.setStatus("PENDING");
        payment.setRemark(cmd.getRemark());
        paymentMapper.insert(payment);

        log.info("创建付款单：paymentNo={}, invoiceId={}, amount={}", payment.getPaymentNo(), cmd.getInvoiceId(), paymentAmount);
        return payment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        PoPayment payment = requirePayment(id);
        if (!"PENDING".equals(payment.getStatus())) {
            throw BizException.statusNotAllow("仅待付款的付款单可以确认");
        }
        payment.setStatus("PAID");
        paymentMapper.updateById(payment);

        // 同步发票状态为 PAID
        PoInvoice invoice = invoiceMapper.selectById(payment.getInvoiceId());
        if (invoice != null) {
            invoice.setInvoiceStatus("PAID");
            invoiceMapper.updateById(invoice);
        }

        cacheEvictor.evictOrderRelated();
        log.info("确认付款：paymentNo={}", payment.getPaymentNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) {
        PoPayment payment = requirePayment(id);
        if (!"PENDING".equals(payment.getStatus())) {
            throw BizException.statusNotAllow("仅待付款的付款单可以驳回");
        }
        payment.setStatus("REJECTED");
        paymentMapper.updateById(payment);
        log.info("驳回付款：paymentNo={}", payment.getPaymentNo());
    }

    // ==================== 私有方法 ====================

    private PoPayment requirePayment(Long id) {
        PoPayment payment = paymentMapper.selectById(id);
        if (payment == null) {
            throw BizException.notFound("付款单不存在");
        }
        return payment;
    }

    private PaymentListVO toListVO(PoPayment payment) {
        PaymentListVO vo = new PaymentListVO();
        vo.setId(payment.getId());
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setInvoiceId(payment.getInvoiceId());
        vo.setOrderId(payment.getOrderId());
        vo.setSupplierId(payment.getSupplierId());
        vo.setPaymentDate(payment.getPaymentDate());
        vo.setPaymentAmount(payment.getPaymentAmount());
        vo.setPaymentMethod(payment.getPaymentMethod());
        vo.setStatus(payment.getStatus());
        vo.setCreateTime(payment.getCreateTime());

        PoInvoice invoice = invoiceMapper.selectById(payment.getInvoiceId());
        vo.setInvoiceNo(invoice == null ? null : invoice.getInvoiceNo());

        PoOrder order = orderMapper.selectById(payment.getOrderId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());

        Supplier supplier = supplierMapper.selectById(payment.getSupplierId());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());

        return vo;
    }

    private String generatePaymentNo() {
        String date = LocalDate.now().format(DATE_PATTERN);
        for (int i = 0; i < 3; i++) {
            Long seq = cacheService.increment(RedisKeys.sequence("PAY", date), Duration.ofDays(1));
            if (seq == null) {
                long count = paymentMapper.selectCount(new LambdaQueryWrapper<PoPayment>()
                        .likeRight(PoPayment::getPaymentNo, "PAY" + date));
                seq = count + 1;
            }
            String no = "PAY" + date + String.format("%04d", seq);
            if (paymentMapper.selectCount(new LambdaQueryWrapper<PoPayment>().eq(PoPayment::getPaymentNo, no)) == 0) {
                return no;
            }
        }
        return "PAY" + date + String.format("%04d", System.currentTimeMillis() % 10000);
    }
}
