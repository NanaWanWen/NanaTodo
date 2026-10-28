package com.yonerp.po.invoice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.ap.event.InvoiceCheckedEvent;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.CacheEvictor;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.invoice.dto.InvoiceCreateCmd;
import com.yonerp.po.invoice.dto.InvoiceQuery;
import com.yonerp.po.invoice.dto.InvoiceRedFlushCmd;
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
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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

        // 同一收货单同时只允许一张「有效蓝字发票」（未红冲/未作废），
        // 更正走「红冲原票 → 重开」，防止无限重开导致净额成倍虚增
        long activeBlue = invoiceMapper.selectCount(new LambdaQueryWrapper<PoInvoice>()
                .eq(PoInvoice::getReceiptId, cmd.getReceiptId())
                .eq(PoInvoice::getInvoiceType, "BLUE")
                .notIn(PoInvoice::getInvoiceStatus, "RED_FLUSHED", "VOIDED"));
        if (activeBlue > 0) {
            throw new BizException(2004, "该收货单已存在有效蓝字发票，如需更正请先「红冲」原发票后再重开");
        }

        // 金额优先取前端传入（退票重开等手工场景），否则按收货单行级计算
        List<PoReceiptItem> items = receiptItemMapper.selectList(
                new LambdaQueryWrapper<PoReceiptItem>().eq(PoReceiptItem::getReceiptId, cmd.getReceiptId()));
        BigDecimal invoiceAmount;
        BigDecimal taxAmount;
        if (cmd.getInvoiceAmount() != null && cmd.getTaxAmount() != null) {
            invoiceAmount = cmd.getInvoiceAmount();
            taxAmount = cmd.getTaxAmount();
        } else {
            invoiceAmount = BigDecimal.ZERO;
            taxAmount = BigDecimal.ZERO;
            for (PoReceiptItem item : items) {
                invoiceAmount = invoiceAmount.add(item.getReceivedQty().multiply(item.getUnitPrice()));
                taxAmount = taxAmount.add(item.getTaxAmount());
            }
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
        // 新建发票强制蓝字：红字发票只能通过「红冲」动作生成，保证红蓝严格对应
        invoice.setInvoiceType("BLUE");
        invoice.setRelatedInvoiceId(cmd.getRelatedInvoiceId());
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

        // 发布发票校验事件（事务提交后异步触发自动结算和暂估回冲）
        eventPublisher.publishEvent(new InvoiceCheckedEvent(this, invoice.getId(), invoice.getOrderId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uncheck(Long id) {
        PoInvoice invoice = requireInvoice(id);
        if ("RED".equals(invoice.getInvoiceType())) {
            throw BizException.statusNotAllow("红字发票是红冲链的一环，不可撤销核对");
        }
        if (!"CHECKED".equals(invoice.getInvoiceStatus())) {
            throw BizException.statusNotAllow("仅已核对的发票可以撤销核对");
        }
        invoice.setInvoiceStatus("UNCHECKED");
        invoiceMapper.updateById(invoice);
        log.info("发票撤销核对：invoiceNo={}", invoice.getInvoiceNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, com.yonerp.po.invoice.dto.InvoiceUpdateCmd cmd) {
        PoInvoice invoice = requireInvoice(id);
        if ("RED".equals(invoice.getInvoiceType())) {
            throw BizException.statusNotAllow("红字发票金额与原票严格对应，不可修改");
        }
        if (!"UNCHECKED".equals(invoice.getInvoiceStatus())) {
            throw BizException.statusNotAllow("仅未核对的发票可以修改金额");
        }
        invoice.setInvoiceAmount(cmd.getInvoiceAmount());
        invoice.setTaxAmount(cmd.getTaxAmount());
        invoice.setTotalAmount(cmd.getInvoiceAmount().add(cmd.getTaxAmount()));
        if (cmd.getInvoiceDate() != null) {
            invoice.setInvoiceDate(cmd.getInvoiceDate());
        }
        if (cmd.getRemark() != null) {
            invoice.setRemark(cmd.getRemark());
        }
        invoiceMapper.updateById(invoice);
        log.info("修改未核对发票金额：invoiceNo={}, invoiceAmount={}, taxAmount={}",
                invoice.getInvoiceNo(), invoice.getInvoiceAmount(), invoice.getTaxAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long redFlush(Long id, com.yonerp.po.invoice.dto.InvoiceRedFlushCmd cmd) {
        PoInvoice original = requireInvoice(id);
        if (!"CHECKED".equals(original.getInvoiceStatus())) {
            throw BizException.statusNotAllow("仅已核对的发票可以红冲");
        }
        if ("RED".equals(original.getInvoiceType())) {
            throw BizException.statusNotAllow("红字发票不可再次红冲");
        }
        if (original.getRelatedInvoiceId() != null) {
            throw BizException.statusNotAllow("该发票已被红冲，请勿重复操作");
        }

        // 生成红字发票：金额为原票相反数，状态未核对，关联原票，并自动核对生效
        PoInvoice red = new PoInvoice();
        red.setInvoiceNo(generateRedInvoiceNo());
        red.setReceiptId(original.getReceiptId());
        red.setOrderId(original.getOrderId());
        red.setSupplierId(original.getSupplierId());
        red.setInvoiceDate(LocalDate.now());
        red.setInvoiceAmount(original.getInvoiceAmount().negate());
        red.setTaxAmount(original.getTaxAmount().negate());
        red.setTotalAmount(original.getTotalAmount().negate());
        red.setInvoiceType("RED");
        red.setRelatedInvoiceId(original.getId());
        red.setRelatedInvoiceNo(original.getInvoiceNo());
        red.setInvoiceStatus("CHECKED");
        red.setRemark("红冲发票 " + original.getInvoiceNo() + (cmd.getRemark() != null ? "：" + cmd.getRemark() : ""));
        invoiceMapper.insert(red);

        // 原票置已红冲
        original.setInvoiceStatus("RED_FLUSHED");
        invoiceMapper.updateById(original);

        log.info("红冲发票：原票={}, 红票={}, 金额={}", original.getInvoiceNo(), red.getInvoiceNo(), red.getTotalAmount());

        // 红票已核对，发布事件触发按净额重匹配（原票+红票 => 净额归零，等待重开蓝票）
        eventPublisher.publishEvent(new InvoiceCheckedEvent(this, red.getId(), red.getOrderId()));
        return red.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidInvoice(Long id) {
        PoInvoice invoice = requireInvoice(id);
        if (!"UNCHECKED".equals(invoice.getInvoiceStatus())) {
            throw BizException.statusNotAllow("仅未核对的草稿发票可以作废，已核对的发票请走红冲");
        }
        if ("RED".equals(invoice.getInvoiceType())) {
            throw BizException.statusNotAllow("红字发票不可作废");
        }
        invoice.setInvoiceStatus("VOIDED");
        invoiceMapper.updateById(invoice);
        log.info("作废草稿发票：invoiceNo={}", invoice.getInvoiceNo());
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
        vo.setInvoiceType(invoice.getInvoiceType());
        vo.setRelatedInvoiceNo(invoice.getRelatedInvoiceNo());
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

    private String generateRedInvoiceNo() {
        String date = LocalDate.now().format(DATE_PATTERN);
        for (int i = 0; i < 3; i++) {
            Long seq = cacheService.increment(RedisKeys.sequence("RED", date), Duration.ofDays(1));
            if (seq == null) {
                long count = invoiceMapper.selectCount(new LambdaQueryWrapper<PoInvoice>()
                        .likeRight(PoInvoice::getInvoiceNo, "RED" + date));
                seq = count + 1;
            }
            String no = "RED" + date + String.format("%04d", seq);
            if (invoiceMapper.selectCount(new LambdaQueryWrapper<PoInvoice>().eq(PoInvoice::getInvoiceNo, no)) == 0) {
                return no;
            }
        }
        return "RED" + date + String.format("%04d", System.currentTimeMillis() % 10000);
    }
}
