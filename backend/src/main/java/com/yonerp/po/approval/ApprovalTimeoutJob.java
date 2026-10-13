package com.yonerp.po.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.enums.ApprovalAction;
import com.yonerp.po.common.enums.OrderStatus;
import com.yonerp.po.order.entity.PoApprovalLog;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoApprovalLogMapper;
import com.yonerp.po.order.mapper.PoOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审批超时提醒任务：每分钟扫描一次，仅打标记写流水，不自动改状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutJob {

    private final StringRedisTemplate stringRedisTemplate;

    private final PoOrderMapper orderMapper;

    private final PoApprovalLogMapper logMapper;

    @Scheduled(fixedDelayString = "60000", initialDelayString = "60000")
    public void scanTimeout() {
        try {
            var members = stringRedisTemplate.opsForZSet()
                    .rangeByScore(RedisKeys.timeoutZset(), 0, System.currentTimeMillis());
            if (members == null || members.isEmpty()) {
                return;
            }
            for (String member : members) {
                handleOne(Long.valueOf(member));
            }
        } catch (Exception e) {
            log.warn("审批超时扫描异常", e);
        }
    }

    private void handleOne(Long orderId) {
        PoOrder order = orderMapper.selectById(orderId);
        if (order == null || !OrderStatus.APPROVING.getCode().equals(order.getStatus())) {
            remove(orderId);
            return;
        }
        long exist = logMapper.selectCount(new LambdaQueryWrapper<PoApprovalLog>()
                .eq(PoApprovalLog::getOrderId, orderId)
                .eq(PoApprovalLog::getAction, ApprovalAction.TIMEOUT_REMIND.getCode()));
        if (exist == 0) {
            PoApprovalLog entity = new PoApprovalLog();
            entity.setOrderId(orderId);
            entity.setAction(ApprovalAction.TIMEOUT_REMIND.getCode());
            entity.setFromStatus(OrderStatus.APPROVING.getCode());
            entity.setToStatus(OrderStatus.APPROVING.getCode());
            entity.setOpinion("审批超时未完成，请尽快处理");
            entity.setOperateTime(LocalDateTime.now());
            logMapper.insert(entity);
            log.info("审批超时提醒：orderNo={}", order.getOrderNo());
        }
        remove(orderId);
    }

    private void remove(Long orderId) {
        stringRedisTemplate.opsForZSet().remove(RedisKeys.timeoutZset(), String.valueOf(orderId));
    }
}
