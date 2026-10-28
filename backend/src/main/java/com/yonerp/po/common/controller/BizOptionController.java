package com.yonerp.po.common.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.vo.BizOption;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.receipt.entity.PoReceipt;
import com.yonerp.po.receipt.mapper.ReceiptMapper;
import com.yonerp.po.invoice.entity.PoInvoice;
import com.yonerp.po.invoice.mapper.InvoiceMapper;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.base.entity.Material;
import com.yonerp.po.base.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务单据选项查询接口（供前端可搜索选择器使用）
 *
 * <p>支持按关键词搜索、状态过滤、分页，返回精简的选项数据。
 */
@Slf4j
@RestController
@RequestMapping("/api/options")
@RequiredArgsConstructor
public class BizOptionController {

    private final PoOrderMapper orderMapper;
    private final ReceiptMapper receiptMapper;
    private final InvoiceMapper invoiceMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;

    private static final Map<String, String> ORDER_STATUS_MAP = Map.of(
            "DRAFT", "草稿", "APPROVING", "审批中", "APPROVED", "已审批",
            "REJECTED", "已驳回", "CANCELED", "已取消", "COMPLETED", "已完成");

    private static final Map<String, String> RECEIPT_STATUS_MAP = Map.of(
            "DRAFT", "草稿", "CONFIRMED", "已确认", "CANCELED", "已取消");

    private static final Map<String, String> INVOICE_STATUS_MAP = Map.of(
            "UNCHECKED", "未核对", "CHECKED", "已核对", "PAID", "已付款");

    /**
     * 查询可收货的订单（已审批/已完成，按关键词搜索）
     */
    @GetMapping("/orders/receivable")
    public Result<PageResult<BizOption>> receivableOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        LambdaQueryWrapper<PoOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(PoOrder::getStatus, "APPROVED", "COMPLETED");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(PoOrder::getOrderNo, keyword)
                    .or().like(PoOrder::getTitle, keyword));
        }
        wrapper.orderByDesc(PoOrder::getCreateTime);

        long total = orderMapper.selectCount(wrapper);
        wrapper.last("LIMIT " + ((pageNo - 1) * pageSize) + ", " + pageSize);
        List<PoOrder> list = orderMapper.selectList(wrapper);

        List<BizOption> options = list.stream().map(order -> BizOption.builder()
                .id(order.getId())
                .no(order.getOrderNo())
                .label(order.getTitle())
                .status(order.getStatus())
                .statusName(ORDER_STATUS_MAP.getOrDefault(order.getStatus(), order.getStatus()))
                .amount(order.getTotalAmount())
                .date(order.getOrderDate() != null ? order.getOrderDate().toString() : null)
                .build()).collect(Collectors.toList());

        PageResult<BizOption> result = new PageResult<>();
        result.setRecords(options);
        result.setTotal(total);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setPages((total + pageSize - 1) / pageSize);
        return Result.ok(result);
    }

    /**
     * 查询可开票的收货单（已确认，按关键词搜索）
     */
    @GetMapping("/receipts/invoicable")
    public Result<PageResult<BizOption>> invoicableReceipts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        LambdaQueryWrapper<PoReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PoReceipt::getStatus, "CONFIRMED");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PoReceipt::getReceiptNo, keyword);
        }
        wrapper.orderByDesc(PoReceipt::getCreateTime);

        long total = receiptMapper.selectCount(wrapper);
        wrapper.last("LIMIT " + ((pageNo - 1) * pageSize) + ", " + pageSize);
        List<PoReceipt> list = receiptMapper.selectList(wrapper);

        List<BizOption> options = list.stream().map(receipt -> BizOption.builder()
                .id(receipt.getId())
                .no(receipt.getReceiptNo())
                .label(receipt.getReceiptNo())
                .status(receipt.getStatus())
                .statusName(RECEIPT_STATUS_MAP.getOrDefault(receipt.getStatus(), receipt.getStatus()))
                .amount(receipt.getTotalAmount())
                .date(receipt.getReceiptDate() != null ? receipt.getReceiptDate().toString() : null)
                .build()).collect(Collectors.toList());

        PageResult<BizOption> result = new PageResult<>();
        result.setRecords(options);
        result.setTotal(total);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setPages((total + pageSize - 1) / pageSize);
        return Result.ok(result);
    }

    /**
     * 查询可付款的发票（已核对，按关键词搜索）
     */
    @GetMapping("/invoices/payable")
    public Result<PageResult<BizOption>> payableInvoices(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        LambdaQueryWrapper<PoInvoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PoInvoice::getInvoiceStatus, "CHECKED");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PoInvoice::getInvoiceNo, keyword);
        }
        wrapper.orderByDesc(PoInvoice::getCreateTime);

        long total = invoiceMapper.selectCount(wrapper);
        wrapper.last("LIMIT " + ((pageNo - 1) * pageSize) + ", " + pageSize);
        List<PoInvoice> list = invoiceMapper.selectList(wrapper);

        List<BizOption> options = list.stream().map(invoice -> BizOption.builder()
                .id(invoice.getId())
                .no(invoice.getInvoiceNo())
                .label(invoice.getInvoiceNo())
                .status(invoice.getInvoiceStatus())
                .statusName(INVOICE_STATUS_MAP.getOrDefault(invoice.getInvoiceStatus(), invoice.getInvoiceStatus()))
                .amount(invoice.getTotalAmount())
                .date(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : null)
                .build()).collect(Collectors.toList());

        PageResult<BizOption> result = new PageResult<>();
        result.setRecords(options);
        result.setTotal(total);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setPages((total + pageSize - 1) / pageSize);
        return Result.ok(result);
    }

    /**
     * 查询供应商（按关键词搜索）
     */
    @GetMapping("/suppliers")
    public Result<List<BizOption>> suppliers(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Supplier::getSupplierCode, keyword)
                    .or().like(Supplier::getSupplierName, keyword)
                    .or().like(Supplier::getContactName, keyword));
        }
        wrapper.eq(Supplier::getStatus, 1).orderByAsc(Supplier::getSupplierName);
        List<Supplier> list = supplierMapper.selectList(wrapper);

        List<BizOption> options = list.stream().map(supplier -> BizOption.builder()
                .id(supplier.getId())
                .no(supplier.getSupplierCode())
                .label(supplier.getSupplierName())
                .subLabel(supplier.getContactName() != null ? supplier.getContactName() : null)
                .build()).collect(Collectors.toList());

        return Result.ok(options);
    }

    /**
     * 查询物料（按关键词搜索）
     */
    @GetMapping("/materials")
    public Result<List<BizOption>> materials(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Material::getMaterialCode, keyword)
                    .or().like(Material::getMaterialName, keyword));
        }
        wrapper.orderByAsc(Material::getMaterialName);
        List<Material> list = materialMapper.selectList(wrapper);

        List<BizOption> options = list.stream().map(material -> BizOption.builder()
                .id(material.getId())
                .no(material.getMaterialCode())
                .label(material.getMaterialName())
                .subLabel(material.getSpec() != null ? material.getSpec() : null)
                .amount(material.getRefPrice())
                .build()).collect(Collectors.toList());

        return Result.ok(options);
    }
}