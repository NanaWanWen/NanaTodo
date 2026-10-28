package com.yonerp.po.importx.core;

import lombok.Getter;

/**
 * Excel 导入业务类型
 */
@Getter
public enum ImportBizType {

    /** 采购订单导入 */
    ORDER("ORDER", "采购订单", "V1"),
    /** 收货单导入 */
    RECEIPT("RECEIPT", "收货单", "V1"),
    /** 采购发票导入 */
    INVOICE("INVOICE", "采购发票", "V1"),
    /** 付款单导入 */
    PAYMENT("PAYMENT", "付款单", "V1");

    private final String code;
    private final String label;
    private final String currentVersion;

    ImportBizType(String code, String label, String currentVersion) {
        this.code = code;
        this.label = label;
        this.currentVersion = currentVersion;
    }

    public static ImportBizType of(String code) {
        for (ImportBizType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知导入业务类型：" + code);
    }
}
