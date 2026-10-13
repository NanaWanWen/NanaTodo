package com.yonerp.po.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 按时间统计（柱状图）
 */
@Data
public class TimeStatVO {

    /** 粒度：month / day */
    private String granularity;

    private List<TimePoint> points = new ArrayList<>();

    /** 数据更新时间（缓存写入时间，便于前端提示） */
    private String dataTime;

    @Data
    public static class TimePoint {

        private String period;

        private Long count;

        private BigDecimal amount;
    }
}
