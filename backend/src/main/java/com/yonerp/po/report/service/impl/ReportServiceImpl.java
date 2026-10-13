package com.yonerp.po.report.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yonerp.po.base.service.DictService;
import com.yonerp.po.base.vo.DictVO;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.report.dto.ReportRankDTO;
import com.yonerp.po.report.dto.ReportStatusDTO;
import com.yonerp.po.report.dto.ReportSummaryDTO;
import com.yonerp.po.report.dto.ReportTimeDTO;
import com.yonerp.po.report.mapper.PoOrderReportMapper;
import com.yonerp.po.report.service.ReportService;
import com.yonerp.po.report.vo.RankResultVO;
import com.yonerp.po.report.vo.RankStatVO;
import com.yonerp.po.report.vo.ReportSummaryVO;
import com.yonerp.po.report.vo.StatusStatVO;
import com.yonerp.po.report.vo.TimeStatVO;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.security.PermissionChecker;
import com.yonerp.po.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表服务实现：单条聚合 SQL + Redis 结果缓存（5 分钟，容忍延迟）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 页面查询 TOP N 上限 */
    private static final int MAX_TOP_N = 100;

    /** 导出最大行数（超过则截断，防止大文件拖垮内存） */
    private static final int EXPORT_MAX_ROWS = 1000;

    private static final TypeReference<List<StatusStatVO>> STATUS_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<TimeStatVO> TIME_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<RankResultVO> RANK_TYPE = new TypeReference<>() {
    };

    private final PoOrderReportMapper reportMapper;

    private final DictService dictService;

    private final CacheService cacheService;

    private final PermissionChecker permissionChecker;

    @Override
    public ReportSummaryVO summary(LocalDate startDate, LocalDate endDate) {
        String scope = scope();
        String hash = hash(startDate, endDate);
        String key = RedisKeys.reportSummary(scope, hash);
        ReportSummaryVO cached = cacheService.get(key, ReportSummaryVO.class).orElse(null);
        if (cached != null) {
            return cached;
        }
        ReportSummaryDTO dto = reportMapper.selectSummary(buyerId(), startDate, endDate);
        ReportSummaryVO vo = new ReportSummaryVO();
        if (dto != null) {
            vo.setTotalCount(dto.getTotalCount());
            vo.setTotalAmount(dto.getTotalAmount() == null ? BigDecimal.ZERO : dto.getTotalAmount());
            vo.setDraftCount(dto.getDraftCount());
            vo.setApprovingCount(dto.getApprovingCount());
            vo.setApprovingAmount(dto.getApprovingAmount() == null ? BigDecimal.ZERO : dto.getApprovingAmount());
            vo.setApprovedCount(dto.getApprovedCount());
            vo.setRejectedCount(dto.getRejectedCount());
            vo.setCanceledCount(dto.getCanceledCount());
        }
        cacheService.set(key, vo, Duration.ofMinutes(5), 60);
        return vo;
    }

    @Override
    public List<StatusStatVO> byStatus(LocalDate startDate, LocalDate endDate) {
        String scope = scope();
        String hash = hash(startDate, endDate);
        String key = RedisKeys.reportStatus(scope, hash);
        List<StatusStatVO> cached = cacheService.get(key, STATUS_TYPE).orElse(null);
        if (cached != null) {
            return cached;
        }
        Map<String, DictVO> dictMap = statusDict();
        List<ReportStatusDTO> rows = reportMapper.selectByStatus(buyerId(), startDate, endDate);
        List<StatusStatVO> result = new ArrayList<>();
        if (rows != null) {
            for (ReportStatusDTO row : rows) {
                StatusStatVO vo = new StatusStatVO();
                vo.setStatus(row.getStatus());
                vo.setCount(row.getCount());
                vo.setAmount(row.getAmount());
                DictVO dict = dictMap.get(row.getStatus());
                vo.setStatusName(dict == null ? row.getStatus() : dict.getDictName());
                vo.setColor(dict == null ? "#8C8C8C" : dict.getDictValue());
                result.add(vo);
            }
        }
        cacheService.set(key, result, Duration.ofMinutes(5), 60);
        return result;
    }

    @Override
    public TimeStatVO byTime(String granularity, LocalDate startDate, LocalDate endDate) {
        String gran = "day".equalsIgnoreCase(granularity) ? "day" : "month";
        String scope = scope();
        String hash = hash(startDate, endDate);
        String key = RedisKeys.reportTime(gran, hash + ":" + scope);
        TimeStatVO cached = cacheService.get(key, TIME_TYPE).orElse(null);
        if (cached != null) {
            return cached;
        }
        List<ReportTimeDTO> rows = reportMapper.selectByTime(buyerId(), gran, startDate, endDate);
        TimeStatVO vo = new TimeStatVO();
        vo.setGranularity(gran);
        vo.setDataTime(LocalDateTime.now().format(TIME_FORMAT));
        if (rows != null) {
            for (ReportTimeDTO row : rows) {
                TimeStatVO.TimePoint point = new TimeStatVO.TimePoint();
                point.setPeriod(row.getPeriod());
                point.setCount(row.getCount());
                point.setAmount(row.getAmount());
                vo.getPoints().add(point);
            }
        }
        cacheService.set(key, vo, Duration.ofMinutes(5), 60);
        return vo;
    }

    @Override
    public RankResultVO rank(String groupBy, LocalDate startDate, LocalDate endDate, String status, Integer topN) {
        return rank(groupBy, startDate, endDate, status, topN, MAX_TOP_N);
    }

    @Override
    public RankResultVO rankForExport(String groupBy, LocalDate startDate, LocalDate endDate, String status) {
        return rank(groupBy, startDate, endDate, status, EXPORT_MAX_ROWS, EXPORT_MAX_ROWS);
    }

    private RankResultVO rank(String groupBy, LocalDate startDate, LocalDate endDate, String status,
                              Integer topN, int maxTopN) {
        String group = normalizeGroupBy(groupBy);
        int limit = normalizeTopN(topN, maxTopN);
        String scope = scope();
        String hash = hash(startDate, endDate) + ":" + limit + ":" + (status == null ? "" : status);
        String key = RedisKeys.reportRank(group, scope, hash);
        RankResultVO cached = cacheService.get(key, RANK_TYPE).orElse(null);
        if (cached != null) {
            return cached;
        }

        Long buyerId = buyerId();
        // 合计行取全口径，不受 TOP N 截断影响
        ReportRankDTO totalDto = reportMapper.selectRankTotal(group, buyerId, startDate, endDate, status);
        BigDecimal totalAmount = totalDto == null || totalDto.getAmount() == null ? BigDecimal.ZERO : totalDto.getAmount();
        BigDecimal totalQty = totalDto == null || totalDto.getQty() == null ? BigDecimal.ZERO : totalDto.getQty();
        long totalCount = totalDto == null || totalDto.getOrderCount() == null ? 0L : totalDto.getOrderCount();

        List<ReportRankDTO> rows = reportMapper.selectRank(group, buyerId, startDate, endDate, status, limit);
        RankResultVO vo = new RankResultVO();
        vo.setGroupBy(group);
        vo.setTopN(limit);
        vo.setDataTime(LocalDateTime.now().format(TIME_FORMAT));
        if (rows != null) {
            for (ReportRankDTO row : rows) {
                RankStatVO item = new RankStatVO();
                item.setCode(row.getCode());
                item.setName(row.getName());
                item.setOrderCount(row.getOrderCount() == null ? 0L : row.getOrderCount());
                item.setQty(row.getQty() == null ? BigDecimal.ZERO : row.getQty());
                item.setAmount(row.getAmount() == null ? BigDecimal.ZERO : row.getAmount());
                item.setLinkValue(String.valueOf(row.getDimId()));
                vo.getRows().add(item);
            }
        }
        // 占比：该项金额 / 全口径金额
        for (RankStatVO item : vo.getRows()) {
            item.setPercent(percent(item.getAmount(), totalAmount));
        }
        RankResultVO.RankTotalVO total = vo.getTotal();
        total.setOrderCount(totalCount);
        total.setQty(totalQty);
        total.setAmount(totalAmount);

        cacheService.set(key, vo, Duration.ofMinutes(5), 60);
        return vo;
    }

    /** 维度白名单，非法值回落为 supplier，避免动态 SQL 拼接风险 */
    private static String normalizeGroupBy(String groupBy) {
        if (groupBy == null) {
            return "supplier";
        }
        return switch (groupBy.trim().toLowerCase()) {
            case "buyer", "material", "dept" -> groupBy.trim().toLowerCase();
            default -> "supplier";
        };
    }

    /** TOP N：默认 10，上限由调用方给定 */
    private static int normalizeTopN(Integer topN, int maxTopN) {
        if (topN == null || topN < 1) {
            return Math.min(10, maxTopN);
        }
        return Math.min(topN, maxTopN);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    /**
     * 数据范围：管理员与审批角色看全部，采购员仅本人
     */
    private Long buyerId() {
        CurrentUser user = UserContext.required();
        return permissionChecker.onlySelf() ? user.getUserId() : null;
    }

    private String scope() {
        Long buyerId = buyerId();
        return buyerId == null ? "ALL" : "USER:" + buyerId;
    }

    private String hash(LocalDate startDate, LocalDate endDate) {
        return Integer.toHexString(String.valueOf(startDate).hashCode()
                ^ String.valueOf(endDate).hashCode());
    }

    private Map<String, DictVO> statusDict() {
        Map<String, DictVO> map = new HashMap<>();
        for (DictVO dict : dictService.list("order_status")) {
            map.put(dict.getDictCode(), dict);
        }
        return map;
    }
}
