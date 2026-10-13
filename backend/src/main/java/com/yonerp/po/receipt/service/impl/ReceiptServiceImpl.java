package com.yonerp.po.receipt.service.impl;

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
import com.yonerp.po.common.enums.OrderStatus;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.inventory.entity.PoInventory;
import com.yonerp.po.inventory.mapper.InventoryMapper;
import com.yonerp.po.inventory.service.InventoryService;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.entity.PoOrderItem;
import com.yonerp.po.order.mapper.PoOrderItemMapper;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.receipt.dto.ReceiptCreateCmd;
import com.yonerp.po.receipt.dto.ReceiptQuery;
import com.yonerp.po.receipt.entity.PoReceipt;
import com.yonerp.po.receipt.entity.PoReceiptItem;
import com.yonerp.po.receipt.mapper.ReceiptItemMapper;
import com.yonerp.po.receipt.mapper.ReceiptMapper;
import com.yonerp.po.receipt.service.ReceiptService;
import com.yonerp.po.receipt.vo.ReceiptDetailVO;
import com.yonerp.po.receipt.vo.ReceiptListVO;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.security.PermissionChecker;
import com.yonerp.po.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采购收货服务实现：创建收货单、确认/取消、库存联动
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReceiptMapper receiptMapper;
    private final ReceiptItemMapper itemMapper;
    private final PoOrderMapper orderMapper;
    private final PoOrderItemMapper orderItemMapper;
    private final SupplierMapper supplierMapper;
    private final InventoryService inventoryService;
    private final CacheService cacheService;
    private final CacheEvictor cacheEvictor;
    private final PermissionChecker permissionChecker;

    @Override
    public PageResult<ReceiptListVO> page(ReceiptQuery query) {
        LambdaQueryWrapper<PoReceipt> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(item -> item.like(PoReceipt::getReceiptNo, query.getKeyword().trim()));
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(PoReceipt::getStatus, query.getStatus());
        }
        if (query.getSupplierId() != null) {
            wrapper.eq(PoReceipt::getSupplierId, query.getSupplierId());
        }
        if (query.getOrderId() != null) {
            wrapper.eq(PoReceipt::getOrderId, query.getOrderId());
        }
        wrapper.orderByDesc(PoReceipt::getCreateTime);

        IPage<PoReceipt> pageResult = receiptMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(pageResult, this::toListVO);
    }

    @Override
    public ReceiptDetailVO detail(Long id) {
        PoReceipt receipt = requireReceipt(id);
        permissionChecker.requireViewable(null);

        ReceiptDetailVO vo = new ReceiptDetailVO();
        vo.setId(receipt.getId());
        vo.setReceiptNo(receipt.getReceiptNo());
        vo.setOrderId(receipt.getOrderId());
        vo.setSupplierId(receipt.getSupplierId());
        vo.setReceiptDate(receipt.getReceiptDate());
        vo.setTotalQty(receipt.getTotalQty());
        vo.setTotalAmount(receipt.getTotalAmount());
        vo.setStatus(receipt.getStatus());
        vo.setRemark(receipt.getRemark());
        vo.setCreateTime(receipt.getCreateTime());

        // 订单编号
        PoOrder order = orderMapper.selectById(receipt.getOrderId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());

        // 供应商名称
        Supplier supplier = supplierMapper.selectById(receipt.getSupplierId());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());

        // 明细
        vo.setItems(loadItems(id));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReceiptCreateCmd cmd) {
        permissionChecker.requireRole(RoleCode.BUYER, RoleCode.ADMIN);
        CurrentUser user = UserContext.required();

        PoOrder order = requireOrder(cmd.getOrderId());
        if (!OrderStatus.APPROVED.getCode().equals(order.getStatus())
                && !OrderStatus.COMPLETED.getCode().equals(order.getStatus())) {
            throw BizException.statusNotAllow("仅已审批/已完成的采购订单可以创建收货单");
        }

        // 根据订单明细构建收货明细
        List<PoOrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<PoOrderItem>()
                        .eq(PoOrderItem::getOrderId, cmd.getOrderId())
                        .eq(PoOrderItem::getDeleted, 0)
                        .orderByAsc(PoOrderItem::getLineNo));

        List<PoReceiptItem> items = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PoOrderItem orderItem : orderItems) {
            BigDecimal receivedQty = BigDecimal.ZERO;
            if (cmd.getItems() != null) {
                for (ReceiptCreateCmd.ItemCmd itemCmd : cmd.getItems()) {
                    if (itemCmd.getOrderItemId() != null && itemCmd.getOrderItemId().equals(orderItem.getId())) {
                        receivedQty = itemCmd.getReceivedQty();
                        break;
                    }
                }
            }
            if (receivedQty == null || receivedQty.compareTo(BigDecimal.ZERO) <= 0) {
                receivedQty = BigDecimal.ZERO;
            }
            if (receivedQty.compareTo(orderItem.getQty()) > 0) {
                throw BizException.param("实收数量不能大于订购数量：物料 " + orderItem.getMaterialName());
            }

            BigDecimal taxAmount = receivedQty.multiply(orderItem.getPrice()).multiply(orderItem.getTaxRate())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal amount = receivedQty.multiply(orderItem.getPrice()).add(taxAmount);

            PoReceiptItem item = new PoReceiptItem();
            item.setOrderItemId(orderItem.getId());
            item.setMaterialId(orderItem.getMaterialId());
            item.setMaterialName(orderItem.getMaterialName());
            item.setSpec(orderItem.getSpec());
            item.setUnit(orderItem.getUnit());
            item.setOrderedQty(orderItem.getQty());
            item.setReceivedQty(receivedQty);
            item.setUnitPrice(orderItem.getPrice());
            item.setTaxRate(orderItem.getTaxRate());
            item.setTaxAmount(taxAmount);
            item.setAmount(amount);
            items.add(item);

            totalQty = totalQty.add(receivedQty);
            totalAmount = totalAmount.add(amount);
        }

        if (totalQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.param("收货数量不能为空，至少录入一行实收数量");
        }

        // 生成收货单编号
        String receiptNo = generateReceiptNo();

        PoReceipt receipt = new PoReceipt();
        receipt.setReceiptNo(receiptNo);
        receipt.setOrderId(cmd.getOrderId());
        receipt.setSupplierId(order.getSupplierId());
        receipt.setReceiptDate(cmd.getReceiptDate() != null ? cmd.getReceiptDate() : LocalDate.now());
        receipt.setTotalQty(totalQty);
        receipt.setTotalAmount(totalAmount);
        receipt.setStatus("DRAFT");
        receipt.setRemark(cmd.getRemark());
        receiptMapper.insert(receipt);

        for (PoReceiptItem item : items) {
            item.setReceiptId(receipt.getId());
            itemMapper.insert(item);
        }

        log.info("创建收货单草稿：receiptNo={}, orderId={}, totalQty={}, totalAmount={}",
                receiptNo, cmd.getOrderId(), totalQty, totalAmount);
        return receipt.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        PoReceipt receipt = requireReceipt(id);
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw BizException.statusNotAllow("仅草稿状态的收货单可以确认");
        }

        // 库存入库
        List<PoReceiptItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PoReceiptItem>().eq(PoReceiptItem::getReceiptId, id));
        for (PoReceiptItem item : items) {
            if (item.getReceivedQty().compareTo(BigDecimal.ZERO) > 0) {
                inventoryService.addStock(item.getMaterialId(), item.getReceivedQty());
            }
        }

        receipt.setStatus("CONFIRMED");
        receiptMapper.updateById(receipt);

        // 如果订单仍为 APPROVED，自动推进为 COMPLETED（简化处理）
        PoOrder order = orderMapper.selectById(receipt.getOrderId());
        if (order != null && OrderStatus.APPROVED.getCode().equals(order.getStatus())) {
            order.setStatus(OrderStatus.COMPLETED.getCode());
            order.setFinishTime(LocalDateTime.now());
            orderMapper.updateById(order);
        }

        cacheEvictor.evictOrderRelated();
        log.info("确认收货：receiptNo={}", receipt.getReceiptNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PoReceipt receipt = requireReceipt(id);
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw BizException.statusNotAllow("仅草稿状态的收货单可以取消");
        }
        receipt.setStatus("CANCELED");
        receiptMapper.updateById(receipt);
        log.info("取消收货：receiptNo={}", receipt.getReceiptNo());
    }

    // ==================== 私有方法 ====================

    private PoReceipt requireReceipt(Long id) {
        PoReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) {
            throw BizException.notFound("收货单不存在");
        }
        return receipt;
    }

    private PoOrder requireOrder(Long id) {
        PoOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("采购订单不存在");
        }
        return order;
    }

    private List<ReceiptDetailVO.ItemVO> loadItems(Long receiptId) {
        List<PoReceiptItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PoReceiptItem>().eq(PoReceiptItem::getReceiptId, receiptId));
        List<ReceiptDetailVO.ItemVO> result = new ArrayList<>();
        for (PoReceiptItem item : items) {
            ReceiptDetailVO.ItemVO vo = new ReceiptDetailVO.ItemVO();
            vo.setId(item.getId());
            vo.setOrderItemId(item.getOrderItemId());
            vo.setMaterialId(item.getMaterialId());
            vo.setMaterialName(item.getMaterialName());
            vo.setSpec(item.getSpec());
            vo.setUnit(item.getUnit());
            vo.setOrderedQty(item.getOrderedQty());
            vo.setReceivedQty(item.getReceivedQty());
            vo.setUnitPrice(item.getUnitPrice());
            vo.setTaxRate(item.getTaxRate());
            vo.setTaxAmount(item.getTaxAmount());
            vo.setAmount(item.getAmount());
            vo.setRemark(item.getRemark());
            result.add(vo);
        }
        return result;
    }

    private ReceiptListVO toListVO(PoReceipt receipt) {
        ReceiptListVO vo = new ReceiptListVO();
        vo.setId(receipt.getId());
        vo.setReceiptNo(receipt.getReceiptNo());
        vo.setOrderId(receipt.getOrderId());
        vo.setSupplierId(receipt.getSupplierId());
        vo.setReceiptDate(receipt.getReceiptDate());
        vo.setTotalQty(receipt.getTotalQty());
        vo.setTotalAmount(receipt.getTotalAmount());
        vo.setStatus(receipt.getStatus());
        vo.setCreateTime(receipt.getCreateTime());

        // 订单编号
        PoOrder order = orderMapper.selectById(receipt.getOrderId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());

        // 供应商名称
        Supplier supplier = supplierMapper.selectById(receipt.getSupplierId());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());

        return vo;
    }

    private String generateReceiptNo() {
        String date = LocalDate.now().format(DATE_PATTERN);
        for (int i = 0; i < 3; i++) {
            Long seq = cacheService.increment(RedisKeys.sequence("RCPT", date), Duration.ofDays(1));
            if (seq == null) {
                long count = receiptMapper.selectCount(new LambdaQueryWrapper<PoReceipt>()
                        .likeRight(PoReceipt::getReceiptNo, "RCPT" + date));
                seq = count + 1;
            }
            String no = "RCPT" + date + String.format("%04d", seq);
            if (receiptMapper.selectCount(new LambdaQueryWrapper<PoReceipt>().eq(PoReceipt::getReceiptNo, no)) == 0) {
                return no;
            }
        }
        return "RCPT" + date + String.format("%04d", System.currentTimeMillis() % 10000);
    }
}
