package com.yonerp.po.report.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细导出行
 */
@Data
public class OrderExcelRow {

    @ExcelProperty("订单编号")
    private String orderNo;

    @ExcelProperty("标题")
    private String title;

    @ExcelProperty("供应商")
    private String supplierName;

    @ExcelProperty("采购员")
    private String buyerName;

    @ExcelProperty("订单日期")
    private String orderDate;

    @ExcelProperty("数量")
    private BigDecimal totalQty;

    @ExcelProperty("含税金额")
    private BigDecimal totalAmount;

    @ExcelProperty("状态")
    private String statusName;

    @ExcelProperty("当前节点")
    private String currentNode;
}
