package com.yonerp.po.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 维度排行统计结果（含合计行）
 */
@Data
public class RankResultVO {

    /** 分组维度：supplier / buyer / material / dept */
    private String groupBy;

    /** 实际生效的 TOP N */
    private Integer topN;

    private List<RankStatVO> rows = new ArrayList<>();

    /** 表尾合计 */
    private RankTotalVO total = new RankTotalVO();

    /** 数据更新时间（缓存写入时间，便于前端提示） */
    private String dataTime;

    @Data
    public static class RankTotalVO {

        private Long orderCount;

        private BigDecimal qty;

        private BigDecimal amount;
    }
}
