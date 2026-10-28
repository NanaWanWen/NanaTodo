package com.yonerp.po.ap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yonerp.po.ap.dto.AccrualQuery;
import com.yonerp.po.ap.entity.PoAccrual;
import com.yonerp.po.ap.mapper.AccrualMapper;
import com.yonerp.po.ap.service.AccrualService;
import com.yonerp.po.ap.vo.AccrualVO;
import com.yonerp.po.base.entity.Material;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.mapper.MaterialMapper;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.invoice.entity.PoInvoice;
import com.yonerp.po.invoice.mapper.InvoiceMapper;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.receipt.entity.PoReceipt;
import com.yonerp.po.receipt.entity.PoReceiptItem;
import com.yonerp.po.receipt.mapper.ReceiptItemMapper;
import com.yonerp.po.receipt.mapper.ReceiptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 暂估应付服务实现（用友式：货到票未到暂估，票到回冲，差额进成本差异）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccrualServiceImpl implements AccrualService {

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter PERIOD_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AccrualMapper accrualMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptItemMapper receiptItemMapper;
    private final PoOrderMapper orderMapper;
    private final InvoiceMapper invoiceMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PoAccrual> accrueByReceipt(Long receiptId) {
        PoReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            log.warn("暂估跳过：收货单 {} 不存在", receiptId);
            return new ArrayList<>();
        }
        if (!"CONFIRMED".equals(receipt.getStatus())) {
            log.info("暂估跳过：收货单 {} 未确认（status={}）", receiptId, receipt.getStatus());
            return new ArrayList<>();
        }

        // 票已到（已核对/已付款）的不再暂估（按订单判断，兼容合单开票）
        long invoiced = invoiceMapper.selectCount(
            new LambdaQueryWrapper<PoInvoice>()
                .eq(PoInvoice::getOrderId, receipt.getOrderId())
                .in(PoInvoice::getInvoiceStatus, "CHECKED", "PAID")
        );
        if (invoiced > 0) {
            log.info("暂估跳过：订单 {} 发票已到，无需暂估", receipt.getOrderId());
            return new ArrayList<>();
        }

        // 已暂估未回冲的不重复生成
        long count = accrualMapper.selectCount(
            new LambdaQueryWrapper<PoAccrual>()
                .eq(PoAccrual::getReceiptId, receiptId)
                .eq(PoAccrual::getStatus, "ACCRUED")
        );
        if (count > 0) {
            log.info("暂估跳过：收货单 {} 已暂估", receiptId);
            return new ArrayList<>();
        }

        PoOrder order = orderMapper.selectById(receipt.getOrderId());
        if (order == null) {
            log.warn("暂估跳过：订单 {} 不存在", receipt.getOrderId());
            return new ArrayList<>();
        }

        List<PoReceiptItem> items = receiptItemMapper.selectList(
            new LambdaQueryWrapper<PoReceiptItem>()
                .eq(PoReceiptItem::getReceiptId, receiptId)
        );
        if (items.isEmpty()) {
            log.warn("暂估跳过：收货单 {} 无明细", receiptId);
            return new ArrayList<>();
        }

        LocalDate today = LocalDate.now();
        List<PoAccrual> created = new ArrayList<>();
        // 按物料分行暂估：暂估单价取订单明细价（无订单价时取收货单价），金额 = 数量 × 单价
        for (PoReceiptItem item : items) {
            BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : ZERO;
            BigDecimal qty = item.getReceivedQty() != null ? item.getReceivedQty() : ZERO;
            BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);

            PoAccrual accrual = new PoAccrual();
            accrual.setAccrualNo(generateAccrualNo());
            accrual.setReceiptId(receiptId);
            accrual.setOrderId(receipt.getOrderId());
            accrual.setSupplierId(receipt.getSupplierId());
            accrual.setMaterialId(item.getMaterialId());
            accrual.setAccrualQty(qty);
            accrual.setAccrualPrice(price);
            accrual.setAccrualAmount(amount);
            accrual.setAccrualDate(today);
            accrual.setAccrualPeriod(today.format(PERIOD_PATTERN));
            accrual.setStatus("ACCRUED");
            accrual.setAccrualMode("SINGLE");
            accrual.setRemark("货到票未到，按收货单价暂估");
            accrualMapper.insert(accrual);
            created.add(accrual);
        }

        log.info("暂估完成：receiptId={}, 行数={}, 总金额={}",
            receiptId, created.size(),
            created.stream().map(PoAccrual::getAccrualAmount).reduce(ZERO, BigDecimal::add));
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseByInvoice(Long invoiceId) {
        PoInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            log.warn("回冲跳过：发票 {} 不存在", invoiceId);
            return;
        }

        // 查找该订单下未回冲的暂估记录（按订单而非收货单，支持一张发票合单开具多张收货单）
        List<PoAccrual> accruals = accrualMapper.selectList(
            new LambdaQueryWrapper<PoAccrual>()
                .eq(PoAccrual::getOrderId, invoice.getOrderId())
                .eq(PoAccrual::getStatus, "ACCRUED")
        );
        if (accruals.isEmpty()) {
            log.info("回冲跳过：订单 {} 无暂估记录", invoice.getOrderId());
            return;
        }

        // 票到回冲：红冲暂估金额，暂估与发票实际的差额按各行占比分摊（进材料成本差异）
        BigDecimal accrualTotal = accruals.stream()
            .map(PoAccrual::getAccrualAmount)
            .reduce(ZERO, BigDecimal::add);
        BigDecimal invoiceTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : ZERO;
        BigDecimal receiptDiff = invoiceTotal.subtract(accrualTotal);

        for (PoAccrual accrual : accruals) {
            accrual.setStatus("REVERSED");
            accrual.setReverseInvoiceId(invoiceId);
            accrual.setReverseDate(LocalDate.now());
            accrual.setReverseAmount(accrual.getAccrualAmount());
            if (accrualTotal.compareTo(ZERO) > 0 && accrual.getAccrualAmount() != null) {
                accrual.setDiffAmount(receiptDiff.multiply(accrual.getAccrualAmount())
                    .divide(accrualTotal, 2, RoundingMode.HALF_UP));
            } else {
                accrual.setDiffAmount(receiptDiff);
            }
            accrualMapper.updateById(accrual);

            log.info("回冲完成：accrualNo={}, invoiceId={}, diff={}",
                accrual.getAccrualNo(), invoiceId, accrual.getDiffAmount());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PoAccrual> monthlyReverse(String period) {
        List<PoAccrual> accruals = accrualMapper.selectList(
            new LambdaQueryWrapper<PoAccrual>()
                .eq(PoAccrual::getAccrualPeriod, period)
                .eq(PoAccrual::getStatus, "ACCRUED")
        );

        List<PoAccrual> reversed = new ArrayList<>();
        for (PoAccrual accrual : accruals) {
            accrual.setStatus("REVERSED");
            accrual.setReverseDate(LocalDate.now());
            accrual.setAccrualMode("MONTHLY");
            accrual.setRemark("月初统一回冲");
            accrualMapper.updateById(accrual);
            reversed.add(accrual);
        }

        log.info("月初批量回冲完成：period={}, count={}", period, reversed.size());
        return reversed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PoAccrual> monthlyAccrue(String period) {
        // 月末批量暂估：扫当期内已确认收货、且暂无已核对/已付款发票的收货单（货到票未到）
        List<PoReceipt> receipts = receiptMapper.selectList(
            new LambdaQueryWrapper<PoReceipt>()
                .eq(PoReceipt::getStatus, "CONFIRMED")
                .apply("DATE_FORMAT(receipt_date, '%Y-%m') = {0}", period)
                .orderByAsc(PoReceipt::getReceiptDate)
        );

        List<PoAccrual> created = new ArrayList<>();
        for (PoReceipt receipt : receipts) {
            // 票已到（已核对/已付款）的不再暂估（按订单判断，兼容合单开票）
            long invoiced = invoiceMapper.selectCount(
                new LambdaQueryWrapper<PoInvoice>()
                    .eq(PoInvoice::getOrderId, receipt.getOrderId())
                    .in(PoInvoice::getInvoiceStatus, "CHECKED", "PAID")
            );
            if (invoiced > 0) {
                continue;
            }
            created.addAll(accrueByReceipt(receipt.getId()));
        }

        log.info("月末批量暂估完成：period={}, 收货单={}, 暂估行={}", period, receipts.size(), created.size());
        return created;
    }

    @Override
    public PageResult<AccrualVO> page(AccrualQuery query) {
        LambdaQueryWrapper<PoAccrual> wrapper = new LambdaQueryWrapper<>();
        if (query.getPeriod() != null && !query.getPeriod().isBlank()) {
            wrapper.eq(PoAccrual::getAccrualPeriod, query.getPeriod().trim());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(PoAccrual::getStatus, query.getStatus().trim());
        }
        if (query.getSupplierId() != null) {
            wrapper.eq(PoAccrual::getSupplierId, query.getSupplierId());
        }
        if (query.getOrderId() != null) {
            wrapper.eq(PoAccrual::getOrderId, query.getOrderId());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(PoAccrual::getAccrualNo, query.getKeyword().trim());
        }
        wrapper.eq(PoAccrual::getDeleted, 0);
        wrapper.orderByDesc(PoAccrual::getAccrualDate).orderByDesc(PoAccrual::getId);

        Page<PoAccrual> page = accrualMapper.selectPage(
            new Page<>(query.getPageNo() > 0 ? query.getPageNo() : 1,
                query.getPageSize() > 0 ? query.getPageSize() : 20),
            wrapper
        );
        return PageResult.of(page, this::toVO);
    }

    @Override
    public String generateAccrualNo() {
        String date = LocalDate.now().format(DATE_PATTERN);
        String prefix = "ACC" + date;

        // 查询当天最大序号
        LambdaQueryWrapper<PoAccrual> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(PoAccrual::getAccrualNo, prefix);
        wrapper.orderByDesc(PoAccrual::getAccrualNo);
        wrapper.last("LIMIT 1");

        PoAccrual last = accrualMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getAccrualNo() != null) {
            String lastNo = last.getAccrualNo();
            String seqStr = lastNo.substring(prefix.length());
            try {
                seq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        return prefix + String.format("%04d", seq);
    }

    private AccrualVO toVO(PoAccrual a) {
        AccrualVO vo = new AccrualVO();
        vo.setId(a.getId());
        vo.setAccrualNo(a.getAccrualNo());
        vo.setReceiptId(a.getReceiptId());
        vo.setOrderId(a.getOrderId());
        vo.setSupplierId(a.getSupplierId());
        vo.setMaterialId(a.getMaterialId());
        vo.setAccrualQty(a.getAccrualQty());
        vo.setAccrualPrice(a.getAccrualPrice());
        vo.setAccrualAmount(a.getAccrualAmount());
        vo.setAccrualDate(a.getAccrualDate());
        vo.setAccrualPeriod(a.getAccrualPeriod());
        vo.setStatus(a.getStatus());
        vo.setReverseInvoiceId(a.getReverseInvoiceId());
        vo.setReverseDate(a.getReverseDate());
        vo.setReverseAmount(a.getReverseAmount());
        vo.setDiffAmount(a.getDiffAmount());
        vo.setAccrualMode(a.getAccrualMode());
        vo.setRemark(a.getRemark());
        vo.setCreateTime(a.getCreateTime());

        if (a.getReceiptId() != null) {
            PoReceipt receipt = receiptMapper.selectById(a.getReceiptId());
            if (receipt != null) {
                vo.setReceiptNo(receipt.getReceiptNo());
            }
        }
        if (a.getOrderId() != null) {
            PoOrder order = orderMapper.selectById(a.getOrderId());
            if (order != null) {
                vo.setOrderNo(order.getOrderNo());
            }
        }
        if (a.getSupplierId() != null) {
            Supplier supplier = supplierMapper.selectById(a.getSupplierId());
            if (supplier != null) {
                vo.setSupplierName(supplier.getSupplierName());
            }
        }
        if (a.getMaterialId() != null) {
            Material material = materialMapper.selectById(a.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            }
        }
        if (a.getReverseInvoiceId() != null) {
            PoInvoice invoice = invoiceMapper.selectById(a.getReverseInvoiceId());
            if (invoice != null) {
                vo.setReverseInvoiceNo(invoice.getInvoiceNo());
            }
        }
        return vo;
    }
}
