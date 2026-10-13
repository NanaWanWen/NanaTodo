package com.yonerp.po.invoice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.CacheEvictor;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.invoice.dto.InvoiceCreateCmd;
import com.yonerp.po.invoice.dto.InvoiceQuery;
import com.yonerp.po.invoice.entity.PoInvoice;
import com.yonerp.po.invoice.mapper.InvoiceMapper;
import com.yonerp.po.invoice.service.InvoiceService;
import com.yonerp.po.invoice.vo.InvoiceListVO;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.receipt.entity.PoReceipt;
import com.yonerp.po.receipt.entity.PoReceiptItem;
import com.yonerp.po.receipt.mapper.ReceiptItemMapper;
import com.yonerp.po.receipt.mapper.ReceiptMapper;
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
 * 采购发票服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InvoiceMapper invoiceMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptItemMapper receiptItemMapper;
    private final PoOrderMapper orderMapper;
    private final SupplierMapper supplierMapper;
    private final CacheService cacheService;
    private final CacheEvictor cacheEvictor;
    private final PermissionChecker permissionChecker;

    @Override
    public PageResult<InvoiceListVO> page(InvoiceQuery query) {
        LambdaQueryWrapper<PoInvoice> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(PoInvoice::getInvoiceNo, query.getKeyword().trim());
        }
        if (query.getInvoiceStatus() != null && !query.getInvoiceStatus().isBlank()) {
            wrapper.eq(PoInvoice::getInvoiceStatus, query.getInvoiceStatus());
        }
        if (query.getSupplierId() != null) {
            wrapper.eq(PoInvoice::getSupplierId, query.getSupplierId());
        }
        if (query.getOrderId() != null) {
            wrapper.eq(PoInvoice::getOrderId, query.getOrderId());
        }
        wrapper.orderByDesc(PoInvoice::getCreateTime);

        IPage<PoInvoice> pageResult = invoiceMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(pageResult, this::toListVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(InvoiceCreateCmd cmd) {
        permissionChecker.requireRole(RoleCode.BUYER, RoleCode.ADMIN);
        CurrentUser user = UserContext.required();

        PoReceipt receipt = receiptMapper.selectById(cmd.getReceiptId());
        if (receipt == null) {
            throw BizException.notFound("收货单不存在");
        }
        if (!"CONFIRMED".equals(receipt.getStatus())) {
            throw BizException.statusNotAllow("仅已确认的收货单可以开具发票");
        }

        // 校验是否已为收货单创建过发票
        long exists = invoiceMapper.selectCount(new LambdaQueryWrapper<PoInvoice>()
                .eq(PoInvoice::getReceiptId, cmd.getReceiptId()));
        if (exists > 0) {
            throw new BizException(2004, "该收货单已开具发票，请勿重复创建");
        }

        // 金额基于收货单总额计算（发票金额 = 收货不含税金额，税额 = 价税合计 - 不含税）
        List<PoReceiptItem> items = receiptItemMapper.selectList(
                new LambdaQueryWrapper<PoReceiptItem>().eq(PoReceiptItem::getReceiptId, cmd.getReceiptId()));
        BigDecimal invoiceAmount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        for (PoReceiptItem item : items) {
            invoiceAmount = invoiceAmount.add(item.getReceivedQty().multiply(item.getUnitPrice()));
            taxAmount = taxAmount.add(item.getTaxAmount());
        }

        PoInvoice invoice = new PoInvoice();
        invoice.setInvoiceNo(generateInvoiceNo());
        invoice.setReceiptId(cmd.getReceiptId());
        invoice.setOrderId(receipt.getOrderId());
        invoice.setSupplierId(receipt.getSupplierId());
        invoice.setInvoiceDate(cmd.getInvoiceDate() != null ? cmd.getInvoiceDate() : LocalDate.now());
        invoice.setInvoiceAmount(invoiceAmount);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(invoiceAmount.add(taxAmount));
        invoice.setInvoiceStatus("UNCHECKED");
        invoice.setRemark(cmd.getRemark());
        invoiceMapper.insert(invoice);

        log.info("创建发票：invoiceNo={}, receiptId={}, totalAmount={}", invoice.getInvoiceNo(), cmd.getReceiptId(), invoice.getTotalAmount());
        return invoice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void check(Long id) {
        PoInvoice invoice = requireInvoice(id);
        if (!"UNCHECKED".equals(invoice.getInvoiceStatus())) {
            throw BizException.statusNotAllow("仅未核对的发票可以标记核对");
        }
        invoice.setInvoiceStatus("CHECKED");
        invoiceMapper.updateById(invoice);
        log.info("发票核对：invoiceNo={}", invoice.getInvoiceNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uncheck(Long id) {
        PoInvoice invoice = requireInvoice(id);
        if (!"CHECKED".equals(invoice.getInvoiceStatus())) {
            throw BizException.statusNotAllow("仅已核对的发票可以撤销核对");
        }
        invoice.setInvoiceStatus("UNCHECKED");
        invoiceMapper.updateById(invoice);
        log.info("发票撤销核对：invoiceNo={}", invoice.getInvoiceNo());
    }

    // ==================== 私有方法 ====================

    private PoInvoice requireInvoice(Long id) {
        PoInvoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) {
            throw BizException.notFound("发票不存在");
        }
        return invoice;
    }

    private InvoiceListVO toListVO(PoInvoice invoice) {
        InvoiceListVO vo = new InvoiceListVO();
        vo.setId(invoice.getId());
        vo.setInvoiceNo(invoice.getInvoiceNo());
        vo.setReceiptId(invoice.getReceiptId());
        vo.setOrderId(invoice.getOrderId());
        vo.setSupplierId(invoice.getSupplierId());
        vo.setInvoiceDate(invoice.getInvoiceDate());
        vo.setInvoiceAmount(invoice.getInvoiceAmount());
        vo.setTaxAmount(invoice.getTaxAmount());
        vo.setTotalAmount(invoice.getTotalAmount());
        vo.setInvoiceStatus(invoice.getInvoiceStatus());
        vo.setCreateTime(invoice.getCreateTime());

        PoOrder order = orderMapper.selectById(invoice.getOrderId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());

        Supplier supplier = supplierMapper.selectById(invoice.getSupplierId());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());

        return vo;
    }

    private String generateInvoiceNo() {
        String date = LocalDate.now().format(DATE_PATTERN);
        for (int i = 0; i < 3; i++) {
            Long seq = cacheService.increment(RedisKeys.sequence("INV", date), Duration.ofDays(1));
            if (seq == null) {
                long count = invoiceMapper.selectCount(new LambdaQueryWrapper<PoInvoice>()
                        .likeRight(PoInvoice::getInvoiceNo, "INV" + date));
                seq = count + 1;
            }
            String no = "INV" + date + String.format("%04d", seq);
            if (invoiceMapper.selectCount(new LambdaQueryWrapper<PoInvoice>().eq(PoInvoice::getInvoiceNo, no)) == 0) {
                return no;
            }
        }
        return "INV" + date + String.format("%04d", System.currentTimeMillis() % 10000);
    }
}
