package com.yonerp.po.agent.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.ap.entity.PoAccrual;
import com.yonerp.po.ap.mapper.AccrualMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 暂估待回冲预警评估器
 * <p>
 * 判断口径：查找已暂估但超过指定天数仍未回冲的记录。
 * 暂估表示"货到票未到"，长期未回冲可能意味着发票丢失或供应商问题。
 * 阈值单位为 DAYS（天数），默认 30 天。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AccrualPendingEvaluator implements AlertRuleEvaluator {

    private static final int DEFAULT_THRESHOLD_DAYS = 30;
    private static final int SCAN_LIMIT = 200;

    private final AccrualMapper accrualMapper;

    @Override
    public String ruleCode() {
        return "ACCRUAL_PENDING";
    }

    @Override
    public String bizDomain() {
        return "FINANCE";
    }

    @Override
    public String alertType() {
        return "ACCRUAL_PENDING";
    }

    @Override
    public String defaultRuleName() {
        return "暂估待回冲预警";
    }

    @Override
    public List<AlertCandidate> evaluate(AgentAlertRule rule) {
        List<AlertCandidate> candidates = new ArrayList<>();
        int thresholdDays = DEFAULT_THRESHOLD_DAYS;
        if (rule.getThreshold() != null && rule.getThreshold().compareTo(BigDecimal.ZERO) > 0) {
            thresholdDays = rule.getThreshold().intValue();
        }

        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(thresholdDays);
            List<PoAccrual> accruals = accrualMapper.selectList(
                    new LambdaQueryWrapper<PoAccrual>()
                            .eq(PoAccrual::getStatus, "ACCRUED")
                            .lt(PoAccrual::getAccrualDate, cutoffDate)
                            .eq(PoAccrual::getDeleted, 0)
                            .orderByAsc(PoAccrual::getAccrualDate)
                            .last("LIMIT " + SCAN_LIMIT)
            );

            for (PoAccrual accrual : accruals) {
                long pendingDays = ChronoUnit.DAYS.between(accrual.getAccrualDate(), LocalDate.now());
                String detail = String.format(
                        "暂估单（单号 %s，收货单 %s）已暂估 %d 天（阈值 %d 天），暂估金额 %s，请尽快催促供应商开票或核实发票状态。",
                        accrual.getAccrualNo(), accrual.getReceiptId(), pendingDays, thresholdDays,
                        accrual.getAccrualAmount()
                );
                AlertCandidate candidate = AlertCandidate.of(
                        rule, accrual.getId(), accrual.getAccrualNo(),
                        "ACCRUAL", "/accruals",
                        "暂估待回冲预警", detail
                );

                // 超过阈值 2 倍升级为 CRITICAL
                if (pendingDays > thresholdDays * 2) {
                    candidate.setLevel("CRITICAL");
                }

                candidates.add(candidate);
            }
        } catch (Exception e) {
            // 评估失败不影响其他规则
        }
        return candidates;
    }
}
