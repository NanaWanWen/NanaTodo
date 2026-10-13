package com.yonerp.po.report.service;

import com.yonerp.po.report.vo.RankResultVO;
import com.yonerp.po.report.vo.ReportSummaryVO;
import com.yonerp.po.report.vo.StatusStatVO;
import com.yonerp.po.report.vo.TimeStatVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表服务（统计结果缓存 5 分钟）
 */
public interface ReportService {

    /**
     * 汇总卡
     */
    ReportSummaryVO summary(LocalDate startDate, LocalDate endDate);

    /**
     * 按状态统计
     */
    List<StatusStatVO> byStatus(LocalDate startDate, LocalDate endDate);

    /**
     * 按时间统计
     */
    TimeStatVO byTime(String granularity, LocalDate startDate, LocalDate endDate);

    /**
     * 维度排行统计（用友式统计表：分组汇总 + TOP N + 合计 + 占比）
     *
     * @param groupBy   分组维度：supplier / buyer / material / dept
     * @param status    订单状态过滤，为空时按生效口径 APPROVING + APPROVED 统计
     * @param topN      取前 N 条，默认 10，上限 100
     */
    RankResultVO rank(String groupBy, LocalDate startDate, LocalDate endDate, String status, Integer topN);

    /**
     * 导出用排行统计：不截断 TOP N（上限 1000 行）
     */
    RankResultVO rankForExport(String groupBy, LocalDate startDate, LocalDate endDate, String status);
}
