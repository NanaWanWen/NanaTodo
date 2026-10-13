package com.yonerp.po.report.mapper;

import com.yonerp.po.report.dto.ReportRankDTO;
import com.yonerp.po.report.dto.ReportStatusDTO;
import com.yonerp.po.report.dto.ReportSummaryDTO;
import com.yonerp.po.report.dto.ReportTimeDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表统计 Mapper（复杂聚合走 XML）
 */
@Mapper
public interface PoOrderReportMapper {

    ReportSummaryDTO selectSummary(@Param("buyerId") Long buyerId,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    List<ReportStatusDTO> selectByStatus(@Param("buyerId") Long buyerId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    List<ReportTimeDTO> selectByTime(@Param("buyerId") Long buyerId,
                                     @Param("granularity") String granularity,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);

    /**
     * 维度排行聚合：groupBy 由 XML 通过 &lt;choose&gt; 白名单切换，禁止直接拼接 ${}
     */
    List<ReportRankDTO> selectRank(@Param("groupBy") String groupBy,
                                   @Param("buyerId") Long buyerId,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate,
                                   @Param("status") String status,
                                   @Param("topN") int topN);

    /**
     * 全口径合计行（不受 TOP N 截断影响，订单数按订单去重）
     */
    ReportRankDTO selectRankTotal(@Param("groupBy") String groupBy,
                                  @Param("buyerId") Long buyerId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate,
                                  @Param("status") String status);
}
