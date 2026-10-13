package com.yonerp.po.report.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 统计表导出行
 */
@Data
public class RankExcelRow {

    @ExcelProperty("排名")
    private Integer index;

    @ExcelProperty("编码")
    private String code;

    @ExcelProperty("名称")
    private String name;

    @ExcelProperty("订单数")
    private Long orderCount;

    @ExcelProperty("数量")
    private BigDecimal qty;

    @ExcelProperty("含税金额")
    private BigDecimal amount;

    @ExcelProperty("金额占比(%)")
    private BigDecimal percent;
}
