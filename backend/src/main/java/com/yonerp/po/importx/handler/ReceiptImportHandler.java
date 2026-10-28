package com.yonerp.po.importx.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.importx.core.ImportBizHandler;
import com.yonerp.po.importx.core.ImportBizType;
import com.yonerp.po.importx.entity.SysImportError;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.entity.PoOrderItem;
import com.yonerp.po.order.mapper.PoOrderItemMapper;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.receipt.dto.ReceiptCreateCmd;
import com.yonerp.po.receipt.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 收货单 Excel 导入处理器
 *
 * <p>模板列：订单编号、收货日期、实收数量、备注
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptImportHandler implements ImportBizHandler {

    private static final List<String> HEADERS = List.of("订单编号", "收货日期", "实收数量", "备注");

    private final PoOrderMapper orderMapper;
    private final PoOrderItemMapper orderItemMapper;
    private final ReceiptService receiptService;

    @Override
    public ImportBizType bizType() {
        return ImportBizType.RECEIPT;
    }

    @Override
    public String templateVersion() {
        return "V1";
    }

    @Override
    public List<String> templateHeaders() {
        return HEADERS;
    }

    @Override
    public List<SysImportError> validateRow(int rowIndex, Map<String, String> row) {
        List<SysImportError> errors = new ArrayList<>();

        // 1. 订单编号必填且必须存在
        String orderNo = trim(row.get("订单编号"));
        if (isBlank(orderNo)) {
            errors.add(error(rowIndex, "订单编号", orderNo, "ERROR", "REQUIRED_FIELD_MISSING", "订单编号不能为空", "请填写系统中的采购订单编号，如 PO202604140001"));
        } else {
            PoOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PoOrder>().eq(PoOrder::getOrderNo, orderNo));
            if (order == null) {
                errors.add(error(rowIndex, "订单编号", orderNo, "ERROR", "ORDER_NOT_FOUND", "订单不存在", "请核对订单编号，或先创建采购订单"));
            } else if (!"APPROVED".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
                errors.add(error(rowIndex, "订单编号", orderNo, "ERROR", "ORDER_STATUS_NOT_ALLOW",
                        "订单状态为 " + order.getStatus() + "，仅已审批/已完成的订单可收货", "请先完成订单审批"));
            }
        }

        // 2. 实收数量必填且为正数
        String qtyStr = trim(row.get("实收数量"));
        if (isBlank(qtyStr)) {
            errors.add(error(rowIndex, "实收数量", qtyStr, "ERROR", "REQUIRED_FIELD_MISSING", "实收数量不能为空", "请填写大于 0 的数字"));
        } else {
            try {
                BigDecimal qty = new BigDecimal(qtyStr);
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(error(rowIndex, "实收数量", qtyStr, "ERROR", "INVALID_QTY", "实收数量必须大于 0", "请填写正数"));
                } else if (orderNo != null) {
                    // 校验是否超过订购数量
                    PoOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PoOrder>().eq(PoOrder::getOrderNo, orderNo));
                    if (order != null) {
                        List<PoOrderItem> items = orderItemMapper.selectList(
                                new LambdaQueryWrapper<PoOrderItem>().eq(PoOrderItem::getOrderId, order.getId()));
                        BigDecimal totalOrdered = items.stream()
                                .map(PoOrderItem::getQty)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (qty.compareTo(totalOrdered) > 0) {
                            errors.add(error(rowIndex, "实收数量", qtyStr, "ERROR", "QTY_EXCEED_ORDERED",
                                    "实收数量 " + qty + " 超过订单订购数量 " + totalOrdered, "请核对收货数量"));
                        }
                    }
                }
            } catch (NumberFormatException e) {
                errors.add(error(rowIndex, "实收数量", qtyStr, "ERROR", "INVALID_NUMBER", "实收数量格式错误", "请填写数字，如 100 或 100.5"));
            }
        }

        // 3. 收货日期可选，若填写必须可解析
        String dateStr = trim(row.get("收货日期"));
        if (!isBlank(dateStr)) {
            try {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception e) {
                errors.add(error(rowIndex, "收货日期", dateStr, "WARNING", "INVALID_DATE_FORMAT", "日期格式建议 yyyy-MM-dd", "留空将默认当天"));
            }
        }

        return errors;
    }

    @Override
    public List<Long> confirm(List<Map<String, String>> rows) {
        List<Long> bizIds = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String orderNo = trim(row.get("订单编号"));
            PoOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PoOrder>().eq(PoOrder::getOrderNo, orderNo));
            if (order == null) {
                throw new IllegalStateException("订单不存在：" + orderNo);
            }

            List<PoOrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<PoOrderItem>()
                            .eq(PoOrderItem::getOrderId, order.getId())
                            .orderByAsc(PoOrderItem::getLineNo));
            if (items.isEmpty()) {
                throw new IllegalStateException("订单无明细行：" + orderNo);
            }

            BigDecimal qty = new BigDecimal(trim(row.get("实收数量")));
            // 简化处理：全部数量记在第一个明细行（多明细拆分可在后续版本支持）
            PoOrderItem first = items.get(0);

            ReceiptCreateCmd cmd = new ReceiptCreateCmd();
            cmd.setOrderId(order.getId());
            String dateStr = trim(row.get("收货日期"));
            if (!isBlank(dateStr)) {
                cmd.setReceiptDate(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }
            cmd.setRemark(trim(row.get("备注")));

            ReceiptCreateCmd.ItemCmd itemCmd = new ReceiptCreateCmd.ItemCmd();
            itemCmd.setOrderItemId(first.getId());
            itemCmd.setReceivedQty(qty);
            cmd.setItems(List.of(itemCmd));

            Long receiptId = receiptService.create(cmd);
            bizIds.add(receiptId);
            log.info("导入收货单：orderNo={}, receiptId={}", orderNo, receiptId);
        }
        return bizIds;
    }

    // ==================== 私有方法 ====================

    private SysImportError error(int rowIndex, String field, String raw, String level, String code, String message, String suggestion) {
        SysImportError e = new SysImportError();
        e.setRowIndex(rowIndex);
        e.setFieldName(field);
        e.setRawValue(raw);
        e.setErrorLevel(level);
        e.setErrorCode(code);
        e.setErrorMessage(message);
        e.setSuggestion(suggestion);
        return e;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
