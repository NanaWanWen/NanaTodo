package com.yonerp.po.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额计算工具：一律 BigDecimal，四舍五入保留 2 位
 */
public final class MoneyUtils {

    private static final int AMOUNT_SCALE = 2;

    private MoneyUtils() {
    }

    /**
     * 计算明细含税金额：数量 × 单价 × (1 + 税率/100)
     */
    public static BigDecimal calcAmount(BigDecimal qty, BigDecimal price, BigDecimal taxRate) {
        BigDecimal rate = taxRate == null ? BigDecimal.ZERO : taxRate;
        BigDecimal factor = BigDecimal.ONE.add(rate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        return qty.multiply(price).multiply(factor).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        if (values == null) {
            return total;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                total = total.add(value);
            }
        }
        return total.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal scale2(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 判断是否大于 0
     */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
