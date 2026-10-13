package com.yonerp.po.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yonerp.po.approval.OrderStateMachine;
import com.yonerp.po.approval.service.ApprovalService;
import com.yonerp.po.approval.vo.ApprovalTodoVO;
import com.yonerp.po.base.entity.SysUser;
import com.yonerp.po.base.mapper.SysUserMapper;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.CacheEvictor;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.LockService;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.enums.ApprovalAction;
import com.yonerp.po.common.enums.ApprovalNode;
import com.yonerp.po.common.enums.OrderStatus;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.common.enums.TaskStatus;
import com.yonerp.po.order.dto.ActionRequest;
import com.yonerp.po.order.entity.PoApprovalLog;
import com.yonerp.po.order.entity.PoApprovalTask;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.entity.PoOrderItem;
import com.yonerp.po.order.mapper.PoApprovalLogMapper;
import com.yonerp.po.order.mapper.PoApprovalTaskMapper;
import com.yonerp.po.order.mapper.PoOrderItemMapper;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.order.service.OrderService;
import com.yonerp.po.order.vo.OrderDetailVO;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.security.PermissionChecker;
import com.yonerp.po.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审批服务实现：两级串行审批 + 审批流水 + Redis 幂等锁 + 数据库乐观锁
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final PoOrderMapper orderMapper;

    private final PoOrderItemMapper itemMapper;

    private final PoApprovalTaskMapper taskMapper;

    private final PoApprovalLogMapper logMapper;

    private final SysUserMapper userMapper;

    private final OrderService orderService;

    private final OrderStateMachine stateMachine;

    private final PermissionChecker permissionChecker;

    private final LockService lockService;

    private final CacheService cacheService;

    private final CacheEvictor cacheEvictor;

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${yonerp.approval-timeout-hours:48}")
    private long timeoutHours;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long orderId, ActionRequest request) {
        // 幂等判定优先：重复提交返回 4001，而不是被后续状态校验改写为业务错误
        checkIdempotent(request, "submit", orderId);
        PoOrder order = requireOrder(orderId);
        permissionChecker.requireOwnerOrAdmin(order.getBuyerId());
        OrderStatus from = OrderStatus.of(order.getStatus());
        stateMachine.ensureCanSubmit(from);
        requireItems(orderId, order);

        String lockKey = RedisKeys.lockAction(orderId, "submit");
        String token = lockService.tryLock(lockKey, Duration.ofSeconds(10));
        if (token == null) {
            throw BizException.lockFailed();
        }
        try {
            OrderStatus to = OrderStatus.APPROVING;
            PoOrder update = new PoOrder();
            update.setId(orderId);
            update.setStatus(to.getCode());
            update.setCurrentNode(ApprovalNode.MANAGER.getSeq());
            update.setSubmitTime(LocalDateTime.now());
            update.setVersion(versionOf(request, order));
            if (orderMapper.updateById(update) == 0) {
                throw BizException.concurrentConflict();
            }
            resetOrCreateTasks(orderId);
            writeLog(orderId, null, null,
                    from == OrderStatus.REJECTED ? ApprovalAction.RESUBMIT : ApprovalAction.SUBMIT,
                    from.getCode(), to.getCode(), request.getRemark());
            addTimeoutWatch(orderId);
            cacheEvictor.evictOrderRelated();
            log.info("提交审批：orderNo={}, operator={}", order.getOrderNo(), UserContext.currentUserId());
        } finally {
            lockService.unlock(lockKey, token);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long orderId, ActionRequest request) {
        checkIdempotent(request, "withdraw", orderId);
        PoOrder order = requireOrder(orderId);
        permissionChecker.requireOwnerOrAdmin(order.getBuyerId());
        OrderStatus from = OrderStatus.of(order.getStatus());
        stateMachine.ensureCanWithdraw(from);

        String lockKey = RedisKeys.lockAction(orderId, "withdraw");
        String token = lockService.tryLock(lockKey, Duration.ofSeconds(10));
        if (token == null) {
            throw BizException.lockFailed();
        }
        try {
            PoOrder update = new PoOrder();
            update.setId(orderId);
            update.setStatus(OrderStatus.DRAFT.getCode());
            update.setCurrentNode(0);
            update.setVersion(versionOf(request, order));
            if (orderMapper.updateById(update) == 0) {
                throw BizException.concurrentConflict();
            }
            invalidateTasks(orderId, 1);
            writeLog(orderId, null, null, ApprovalAction.WITHDRAW,
                    from.getCode(), OrderStatus.DRAFT.getCode(), request.getRemark());
            removeTimeoutWatch(orderId);
            cacheEvictor.evictOrderRelated();
            log.info("撤回订单：orderNo={}, operator={}", order.getOrderNo(), UserContext.currentUserId());
        } finally {
            lockService.unlock(lockKey, token);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, ActionRequest request) {
        checkIdempotent(request, "cancel", orderId);
        PoOrder order = requireOrder(orderId);
        CurrentUser user = UserContext.required();
        OrderStatus from = OrderStatus.of(order.getStatus());
        stateMachine.ensureCanCancel(from, user.isAdmin());
        permissionChecker.requireOwnerOrAdmin(order.getBuyerId());
        if (!StringUtils.hasText(request.getRemark())) {
            throw new BizException(2003, "取消原因不能为空");
        }

        String lockKey = RedisKeys.lockAction(orderId, "cancel");
        String token = lockService.tryLock(lockKey, Duration.ofSeconds(10));
        if (token == null) {
            throw BizException.lockFailed();
        }
        try {
            PoOrder update = new PoOrder();
            update.setId(orderId);
            update.setStatus(OrderStatus.CANCELED.getCode());
            update.setCurrentNode(0);
            update.setVersion(versionOf(request, order));
            if (orderMapper.updateById(update) == 0) {
                throw BizException.concurrentConflict();
            }
            invalidateTasks(orderId, 1);
            writeLog(orderId, null, null, ApprovalAction.CANCEL,
                    from.getCode(), OrderStatus.CANCELED.getCode(), request.getRemark());
            removeTimeoutWatch(orderId);
            cacheEvictor.evictOrderRelated();
            log.info("取消订单：orderNo={}, operator={}", order.getOrderNo(), user.getUserId());
        } finally {
            lockService.unlock(lockKey, token);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long taskId, ActionRequest request) {
        checkIdempotent(request, "approve", taskId);
        PoApprovalTask task = requireTask(taskId);
        PoOrder order = requireOrder(task.getOrderId());
        OrderStatus from = OrderStatus.of(order.getStatus());
        stateMachine.ensureCanApprove(from);
        ensureTaskPending(task);
        CurrentUser user = requireNodeRole(task);

        String lockKey = RedisKeys.lockApprove(taskId);
        String token = lockService.tryLock(lockKey, Duration.ofSeconds(15));
        if (token == null) {
            throw BizException.lockFailed();
        }
        try {
            PoApprovalTask taskUpdate = new PoApprovalTask();
            taskUpdate.setId(taskId);
            taskUpdate.setStatus(TaskStatus.APPROVED.getCode());
            taskUpdate.setApproverId(user.getUserId());
            taskUpdate.setApproveTime(LocalDateTime.now());
            taskUpdate.setOpinion(request.getRemark());
            taskUpdate.setVersion(request.getVersion() == null ? task.getVersion() : request.getVersion());
            if (taskMapper.updateById(taskUpdate) == 0) {
                throw BizException.concurrentConflict();
            }

            PoOrder orderUpdate = new PoOrder();
            orderUpdate.setId(order.getId());
            orderUpdate.setVersion(order.getVersion());
            ApprovalNode node = ApprovalNode.ofSeq(task.getNodeSeq());
            String toStatus;
            if (node != null && node.hasNext()) {
                ApprovalNode next = node.next();
                activateTask(order.getId(), next.getSeq());
                orderUpdate.setCurrentNode(next.getSeq());
                orderUpdate.setStatus(OrderStatus.APPROVING.getCode());
                toStatus = OrderStatus.APPROVING.getCode();
            } else {
                orderUpdate.setStatus(OrderStatus.APPROVED.getCode());
                orderUpdate.setCurrentNode(0);
                orderUpdate.setFinishTime(LocalDateTime.now());
                toStatus = OrderStatus.APPROVED.getCode();
            }
            if (orderMapper.updateById(orderUpdate) == 0) {
                throw BizException.concurrentConflict();
            }

            writeLog(order.getId(), task.getNodeSeq(), task.getNodeRole(),
                    ApprovalAction.APPROVE, from.getCode(), toStatus, request.getRemark());
            if (OrderStatus.APPROVED.getCode().equals(toStatus)) {
                removeTimeoutWatch(order.getId());
            }
            cacheEvictor.evictOrderRelated();
            log.info("审批通过：orderNo={}, node={}, approver={}", order.getOrderNo(), task.getNodeSeq(), user.getUserId());
        } finally {
            lockService.unlock(lockKey, token);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long taskId, ActionRequest request) {
        checkIdempotent(request, "reject", taskId);
        PoApprovalTask task = requireTask(taskId);
        PoOrder order = requireOrder(task.getOrderId());
        OrderStatus from = OrderStatus.of(order.getStatus());
        stateMachine.ensureCanApprove(from);
        ensureTaskPending(task);
        CurrentUser user = requireNodeRole(task);
        if (!StringUtils.hasText(request.getRemark())) {
            throw new BizException(2003, "驳回意见不能为空");
        }

        String lockKey = RedisKeys.lockApprove(taskId);
        String token = lockService.tryLock(lockKey, Duration.ofSeconds(15));
        if (token == null) {
            throw BizException.lockFailed();
        }
        try {
            PoApprovalTask taskUpdate = new PoApprovalTask();
            taskUpdate.setId(taskId);
            taskUpdate.setStatus(TaskStatus.REJECTED.getCode());
            taskUpdate.setApproverId(user.getUserId());
            taskUpdate.setApproveTime(LocalDateTime.now());
            taskUpdate.setOpinion(request.getRemark());
            taskUpdate.setVersion(request.getVersion() == null ? task.getVersion() : request.getVersion());
            if (taskMapper.updateById(taskUpdate) == 0) {
                throw BizException.concurrentConflict();
            }

            invalidateTasks(order.getId(), task.getNodeSeq() + 1);

            PoOrder orderUpdate = new PoOrder();
            orderUpdate.setId(order.getId());
            orderUpdate.setStatus(OrderStatus.REJECTED.getCode());
            orderUpdate.setCurrentNode(0);
            orderUpdate.setVersion(order.getVersion());
            if (orderMapper.updateById(orderUpdate) == 0) {
                throw BizException.concurrentConflict();
            }

            writeLog(order.getId(), task.getNodeSeq(), task.getNodeRole(),
                    ApprovalAction.REJECT, from.getCode(), OrderStatus.REJECTED.getCode(), request.getRemark());
            removeTimeoutWatch(order.getId());
            cacheEvictor.evictOrderRelated();
            log.info("审批驳回：orderNo={}, node={}, approver={}", order.getOrderNo(), task.getNodeSeq(), user.getUserId());
        } finally {
            lockService.unlock(lockKey, token);
        }
    }

    @Override
    public PageResult<ApprovalTodoVO> todo(String keyword, long pageNo, long pageSize) {
        CurrentUser user = UserContext.required();
        RoleCode role = user.getCurrentRole();
        if (role != RoleCode.PURCHASE_MANAGER && role != RoleCode.DIRECTOR) {
            return PageResult.empty(pageNo, pageSize);
        }
        List<PoApprovalTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PoApprovalTask>()
                .eq(PoApprovalTask::getNodeRole, role.getCode())
                .eq(PoApprovalTask::getStatus, TaskStatus.PENDING.getCode()));
        if (tasks.isEmpty()) {
            return PageResult.empty(pageNo, pageSize);
        }
        Map<Long, PoApprovalTask> taskMap = new HashMap<>();
        for (PoApprovalTask task : tasks) {
            taskMap.put(task.getOrderId(), task);
        }

        LambdaQueryWrapper<PoOrder> wrapper = new LambdaQueryWrapper<PoOrder>()
                .in(PoOrder::getId, taskMap.keySet());
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(item -> item.like(PoOrder::getOrderNo, value).or().like(PoOrder::getTitle, value));
        }
        wrapper.orderByAsc(PoOrder::getSubmitTime);
        IPage<PoOrder> page = orderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Map<Long, String> buyerNames = new HashMap<>();
        List<Long> buyerIds = page.getRecords().stream().map(PoOrder::getBuyerId).distinct().toList();
        for (SysUser buyer : userMapper.selectBatchIds(buyerIds)) {
            buyerNames.put(buyer.getId(), buyer.getRealName());
        }

        LocalDateTime overtimeLine = LocalDateTime.now().minusHours(timeoutHours);
        return PageResult.of(page, order -> {
            PoApprovalTask task = taskMap.get(order.getId());
            ApprovalTodoVO vo = new ApprovalTodoVO();
            vo.setTaskId(task == null ? null : task.getId());
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTitle(order.getTitle());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setBuyerName(buyerNames.get(order.getBuyerId()));
            vo.setSubmitTime(order.getSubmitTime());
            vo.setNodeSeq(task == null ? null : task.getNodeSeq());
            vo.setNodeName(task == null ? null : task.getNodeName());
            vo.setOvertime(order.getSubmitTime() != null && order.getSubmitTime().isBefore(overtimeLine));
            return vo;
        });
    }

    @Override
    public long todoCount() {
        CurrentUser user = UserContext.required();
        RoleCode role = user.getCurrentRole();
        if (role != RoleCode.PURCHASE_MANAGER && role != RoleCode.DIRECTOR) {
            return 0L;
        }
        String key = RedisKeys.badgeTodo(role.getCode());
        Long cached = cacheService.getLong(key);
        if (cached != null) {
            return cached;
        }
        long count = taskMapper.selectCount(new LambdaQueryWrapper<PoApprovalTask>()
                .eq(PoApprovalTask::getNodeRole, role.getCode())
                .eq(PoApprovalTask::getStatus, TaskStatus.PENDING.getCode()));
        cacheService.setLong(key, count, Duration.ofMinutes(2));
        return count;
    }

    @Override
    public OrderDetailVO detailAfterAction(Long orderId) {
        return orderService.detail(orderId);
    }

    // ==================== 私有方法 ====================

    private PoOrder requireOrder(Long orderId) {
        PoOrder order = orderId == null ? null : orderMapper.selectById(orderId);
        if (order == null) {
            throw BizException.notFound("采购订单不存在");
        }
        return order;
    }

    private PoApprovalTask requireTask(Long taskId) {
        PoApprovalTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound("审批任务不存在");
        }
        return task;
    }

    private void ensureTaskPending(PoApprovalTask task) {
        if (!TaskStatus.PENDING.getCode().equals(task.getStatus())) {
            throw BizException.statusNotAllow("该审批节点不在待审批状态");
        }
    }

    /**
     * 校验当前角色与审批节点匹配
     */
    private CurrentUser requireNodeRole(PoApprovalTask task) {
        CurrentUser user = UserContext.required();
        if (user.getCurrentRole() == null || !user.getCurrentRole().getCode().equals(task.getNodeRole())) {
            throw BizException.forbiddenRole();
        }
        return user;
    }

    /**
     * 提交前校验明细非空且金额大于 0
     */
    private void requireItems(Long orderId, PoOrder order) {
        long count = itemMapper.selectCount(new LambdaQueryWrapper<PoOrderItem>()
                .eq(PoOrderItem::getOrderId, orderId));
        if (count == 0) {
            throw new BizException(2002, "订单明细不能为空，至少录入一行物料");
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BizException(2002, "订单金额必须大于 0");
        }
    }

    private Integer versionOf(ActionRequest request, PoOrder order) {
        return request.getVersion() == null ? order.getVersion() : request.getVersion();
    }

    private void checkIdempotent(ActionRequest request, String action, Long bizId) {
        String key = StringUtils.hasText(request.getIdempotencyKey())
                ? request.getIdempotencyKey()
                : action + ":" + bizId + ":" + UserContext.currentUserId() + ":" + UUID.randomUUID();
        if (!lockService.acquireIdempotent(RedisKeys.idem(key), Duration.ofSeconds(10))) {
            throw BizException.duplicateSubmit();
        }
    }

    /**
     * 生成或重置审批任务：首个节点 PENDING，其余 WAITING
     */
    private void resetOrCreateTasks(Long orderId) {
        List<PoApprovalTask> exist = taskMapper.selectList(new LambdaQueryWrapper<PoApprovalTask>()
                .eq(PoApprovalTask::getOrderId, orderId)
                .orderByAsc(PoApprovalTask::getNodeSeq));
        List<ApprovalNode> nodes = ApprovalNode.ordered();
        if (exist.isEmpty()) {
            for (ApprovalNode node : nodes) {
                PoApprovalTask task = new PoApprovalTask();
                task.setOrderId(orderId);
                task.setNodeSeq(node.getSeq());
                task.setNodeRole(node.getRole().getCode());
                task.setNodeName(node.getName());
                task.setStatus(node.isFirst() ? TaskStatus.PENDING.getCode() : TaskStatus.WAITING.getCode());
                task.setVersion(0);
                taskMapper.insert(task);
            }
            return;
        }
        Map<Integer, PoApprovalTask> existMap = new HashMap<>();
        for (PoApprovalTask task : exist) {
            existMap.put(task.getNodeSeq(), task);
        }
        for (ApprovalNode node : nodes) {
            PoApprovalTask task = existMap.get(node.getSeq());
            PoApprovalTask update = new PoApprovalTask();
            update.setId(task == null ? null : task.getId());
            update.setNodeSeq(node.getSeq());
            update.setNodeRole(node.getRole().getCode());
            update.setNodeName(node.getName());
            update.setStatus(node.isFirst() ? TaskStatus.PENDING.getCode() : TaskStatus.WAITING.getCode());
            update.setApproverId(null);
            update.setApproveTime(null);
            update.setOpinion(null);
            update.setVersion(task == null ? 0 : task.getVersion());
            if (task == null) {
                update.setId(null);
                update.setOrderId(orderId);
                taskMapper.insert(update);
            } else {
                taskMapper.updateById(update);
            }
        }
    }

    /**
     * 作废从指定节点起的所有任务
     */
    private void invalidateTasks(Long orderId, int fromSeq) {
        PoApprovalTask update = new PoApprovalTask();
        update.setStatus(TaskStatus.INVALID.getCode());
        taskMapper.update(update, new LambdaQueryWrapper<PoApprovalTask>()
                .eq(PoApprovalTask::getOrderId, orderId)
                .ge(PoApprovalTask::getNodeSeq, fromSeq));
    }

    /**
     * 激活下一节点
     */
    private void activateTask(Long orderId, int seq) {
        PoApprovalTask update = new PoApprovalTask();
        update.setStatus(TaskStatus.PENDING.getCode());
        taskMapper.update(update, new LambdaQueryWrapper<PoApprovalTask>()
                .eq(PoApprovalTask::getOrderId, orderId)
                .eq(PoApprovalTask::getNodeSeq, seq));
    }

    private void writeLog(Long orderId, Integer nodeSeq, String nodeRole, ApprovalAction action,
                          String fromStatus, String toStatus, String opinion) {
        PoApprovalLog logEntity = new PoApprovalLog();
        logEntity.setOrderId(orderId);
        logEntity.setNodeSeq(nodeSeq);
        logEntity.setNodeRole(nodeRole);
        logEntity.setOperatorId(UserContext.currentUserId());
        logEntity.setAction(action.getCode());
        logEntity.setFromStatus(fromStatus);
        logEntity.setToStatus(toStatus);
        logEntity.setOpinion(opinion);
        logEntity.setOperateTime(LocalDateTime.now());
        logMapper.insert(logEntity);
    }

    /**
     * 提交审批后登记超时提醒（仅打标记，不自动改状态）
     */
    private void addTimeoutWatch(Long orderId) {
        try {
            double score = System.currentTimeMillis() + timeoutHours * 3600_000L;
            stringRedisTemplate.opsForZSet().add(RedisKeys.timeoutZset(), String.valueOf(orderId), score);
        } catch (Exception e) {
            log.warn("登记超时提醒失败（不影响审批流程），orderId={}", orderId, e);
        }
    }

    private void removeTimeoutWatch(Long orderId) {
        try {
            stringRedisTemplate.opsForZSet().remove(RedisKeys.timeoutZset(), String.valueOf(orderId));
        } catch (Exception e) {
            log.warn("移除超时提醒失败，orderId={}", orderId, e);
        }
    }

    /**
     * 供定时任务扫描的超时成员
     */
    public List<Long> scanTimeoutOrderIds() {
        try {
            var set = stringRedisTemplate.opsForZSet()
                    .rangeByScore(RedisKeys.timeoutZset(), 0, System.currentTimeMillis());
            if (set == null || set.isEmpty()) {
                return new ArrayList<>();
            }
            List<Long> ids = new ArrayList<>();
            for (String value : set) {
                ids.add(Long.valueOf(value));
            }
            return ids;
        } catch (Exception e) {
            log.warn("扫描超时任务失败", e);
            return new ArrayList<>();
        }
    }
}
