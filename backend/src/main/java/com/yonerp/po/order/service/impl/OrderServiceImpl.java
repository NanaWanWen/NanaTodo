package com.yonerp.po.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yonerp.po.base.entity.Material;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.entity.SysUser;
import com.yonerp.po.base.mapper.MaterialMapper;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.base.mapper.SysUserMapper;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.CacheEvictor;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.MoneyUtils;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.enums.ApprovalAction;
import com.yonerp.po.common.enums.OrderStatus;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.common.enums.TaskStatus;
import com.yonerp.po.order.dto.OrderItemRequest;
import com.yonerp.po.order.dto.OrderQuery;
import com.yonerp.po.order.dto.OrderSaveRequest;
import com.yonerp.po.order.entity.PoApprovalLog;
import com.yonerp.po.order.entity.PoApprovalTask;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.entity.PoOrderItem;
import com.yonerp.po.order.mapper.PoApprovalLogMapper;
import com.yonerp.po.order.mapper.PoApprovalTaskMapper;
import com.yonerp.po.order.mapper.PoOrderItemMapper;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.order.service.OrderService;
import com.yonerp.po.order.vo.ApprovalLogVO;
import com.yonerp.po.order.vo.ApprovalTaskVO;
import com.yonerp.po.order.vo.OrderDetailVO;
import com.yonerp.po.order.vo.OrderItemVO;
import com.yonerp.po.order.vo.OrderListVO;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.security.PermissionChecker;
import com.yonerp.po.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购订单服务实现：主从保存、金额服务端重算、数据权限、分页查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PoOrderMapper orderMapper;

    private final PoOrderItemMapper itemMapper;

    private final PoApprovalTaskMapper taskMapper;

    private final PoApprovalLogMapper logMapper;

    private final SupplierMapper supplierMapper;

    private final MaterialMapper materialMapper;

    private final SysUserMapper userMapper;

    private final CacheService cacheService;

    private final CacheEvictor cacheEvictor;

    private final PermissionChecker permissionChecker;

    @Override
    public PageResult<OrderListVO> page(OrderQuery query) {
        CurrentUser user = UserContext.required();
        LambdaQueryWrapper<PoOrder> wrapper = new LambdaQueryWrapper<>();
        if (permissionChecker.onlySelf()) {
            wrapper.eq(PoOrder::getBuyerId, user.getUserId());
        } else if (query.getBuyerId() != null) {
            wrapper.eq(PoOrder::getBuyerId, query.getBuyerId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item.like(PoOrder::getOrderNo, keyword).or().like(PoOrder::getTitle, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PoOrder::getStatus, query.getStatus());
        }
        if (query.getSupplierId() != null) {
            wrapper.eq(PoOrder::getSupplierId, query.getSupplierId());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(PoOrder::getDeptId, query.getDeptId());
        }
        // 物料在明细行上：用 EXISTS 子查询过滤包含该物料的订单（{0} 由 MP 做参数绑定）
        if (query.getMaterialId() != null) {
            wrapper.exists(
                    "SELECT 1 FROM po_order_item i WHERE i.order_id = po_order.id AND i.deleted = 0 AND i.material_id = {0}",
                    query.getMaterialId());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(PoOrder::getOrderDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(PoOrder::getOrderDate, query.getEndDate());
        }
        wrapper.orderByDesc(PoOrder::getCreateTime);

        IPage<PoOrder> pageResult = orderMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        Map<Long, String> supplierNames = loadSupplierNames(pageResult.getRecords());
        Map<Long, String> userNames = loadUserNames(pageResult.getRecords());

        return PageResult.of(pageResult, order -> {
            OrderListVO vo = new OrderListVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTitle(order.getTitle());
            vo.setSupplierId(order.getSupplierId());
            vo.setSupplierName(supplierNames.get(order.getSupplierId()));
            vo.setBuyerId(order.getBuyerId());
            vo.setBuyerName(userNames.get(order.getBuyerId()));
            vo.setOrderDate(order.getOrderDate());
            vo.setExpectDate(order.getExpectDate());
            vo.setTotalQty(order.getTotalQty());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setCurrentNode(order.getCurrentNode());
            vo.setCreateTime(order.getCreateTime());
            return vo;
        });
    }

    @Override
    public OrderDetailVO detail(Long id) {
        PoOrder order = requireOrder(id);
        permissionChecker.requireViewable(order.getBuyerId());

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTitle(order.getTitle());
        vo.setSupplierId(order.getSupplierId());
        vo.setBuyerId(order.getBuyerId());
        vo.setCurrency(order.getCurrency());
        vo.setOrderDate(order.getOrderDate());
        vo.setExpectDate(order.getExpectDate());
        vo.setTotalQty(order.getTotalQty());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setCurrentNode(order.getCurrentNode());
        vo.setVersion(order.getVersion());
        vo.setRemark(order.getRemark());
        vo.setSubmitTime(order.getSubmitTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setCreateTime(order.getCreateTime());

        Supplier supplier = supplierMapper.selectById(order.getSupplierId());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());
        SysUser buyer = userMapper.selectById(order.getBuyerId());
        vo.setBuyerName(buyer == null ? null : buyer.getRealName());

        vo.setItems(loadItems(order.getId()));
        vo.setTasks(loadTasks(order.getId()));
        vo.setLogs(loadLogs(order.getId()));
        vo.setActions(resolveActions(order, vo.getTasks()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OrderSaveRequest request) {
        permissionChecker.requireRole(RoleCode.BUYER, RoleCode.ADMIN);
        CurrentUser user = UserContext.required();
        checkSupplier(request.getSupplierId());
        List<PoOrderItem> items = buildItems(request.getItems());

        PoOrder order = new PoOrder();
        order.setOrderNo(generateOrderNo());
        order.setTitle(request.getTitle().trim());
        order.setSupplierId(request.getSupplierId());
        order.setBuyerId(user.getUserId());
        order.setDeptId(user.getDeptId());
        order.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency() : "CNY");
        order.setOrderDate(request.getOrderDate());
        order.setExpectDate(request.getExpectDate());
        order.setRemark(request.getRemark());
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setCurrentNode(0);
        applyTotals(order, items);

        orderMapper.insert(order);
        persistItems(order.getId(), items);
        cacheEvictor.evictOrderRelated();
        log.info("新建订单：orderNo={}, buyerId={}, amount={}", order.getOrderNo(), user.getUserId(), order.getTotalAmount());
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OrderSaveRequest request) {
        PoOrder exist = requireOrder(id);
        permissionChecker.requireOwnerOrAdmin(exist.getBuyerId());
        OrderStatus status = OrderStatus.of(exist.getStatus());
        if (status == null || !status.isEditable()) {
            throw BizException.statusNotAllow("仅草稿或已驳回的订单可以编辑");
        }
        checkSupplier(request.getSupplierId());
        List<PoOrderItem> items = buildItems(request.getItems());

        PoOrder update = new PoOrder();
        update.setId(id);
        update.setTitle(request.getTitle().trim());
        update.setSupplierId(request.getSupplierId());
        update.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency() : exist.getCurrency());
        update.setOrderDate(request.getOrderDate());
        update.setExpectDate(request.getExpectDate());
        update.setRemark(request.getRemark());
        update.setVersion(request.getVersion() == null ? exist.getVersion() : request.getVersion());
        applyTotals(update, items);

        if (orderMapper.updateById(update) == 0) {
            throw BizException.concurrentConflict();
        }
        persistItems(id, items);
        cacheEvictor.evictOrderRelated();
        log.info("编辑订单：orderNo={}, amount={}", exist.getOrderNo(), update.getTotalAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PoOrder order = requireOrder(id);
        permissionChecker.requireOwnerOrAdmin(order.getBuyerId());
        if (OrderStatus.of(order.getStatus()) != OrderStatus.DRAFT) {
            throw BizException.statusNotAllow("仅草稿状态的订单可以删除");
        }
        orderMapper.deleteById(id);
        clearItems(id);
        cacheEvictor.evictOrderRelated();
        log.info("删除订单：orderNo={}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    public PoOrder getEntity(Long id) {
        return requireOrder(id);
    }

    // ==================== 私有方法 ====================

    private PoOrder requireOrder(Long id) {
        PoOrder order = id == null ? null : orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("采购订单不存在");
        }
        return order;
    }

    private void checkSupplier(Long supplierId) {
        if (supplierId == null) {
            throw BizException.param("供应商不能为空");
        }
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw BizException.notFound("供应商不存在");
        }
    }

    /**
     * 校验明细并重算金额（不信任前端金额）
     */
    private List<PoOrderItem> buildItems(List<OrderItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException(2002, "订单明细不能为空，至少录入一行物料");
        }
        List<PoOrderItem> items = new ArrayList<>();
        int index = 0;
        for (OrderItemRequest request : requests) {
            Material material = materialMapper.selectById(request.getMaterialId());
            if (material == null) {
                throw BizException.notFound("物料不存在：" + request.getMaterialId());
            }
            if (!MoneyUtils.isPositive(request.getQty()) || !MoneyUtils.isPositive(request.getPrice())) {
                throw new BizException(2002, "明细数量与单价必须大于 0");
            }
            BigDecimal taxRate = request.getTaxRate() == null ? material.getTaxRate() : request.getTaxRate();
            if (taxRate == null) {
                taxRate = BigDecimal.ZERO;
            }
            if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
                throw new BizException(2002, "税率必须在 0 ~ 100 之间");
            }

            PoOrderItem item = new PoOrderItem();
            item.setLineNo(request.getLineNo() == null ? (index + 1) * 10 : request.getLineNo());
            item.setMaterialId(material.getId());
            item.setMaterialName(material.getMaterialName());
            item.setSpec(material.getSpec());
            item.setUnit(material.getUnit());
            item.setQty(request.getQty());
            item.setPrice(request.getPrice());
            item.setTaxRate(taxRate);
            item.setAmount(MoneyUtils.calcAmount(request.getQty(), request.getPrice(), taxRate));
            item.setRemark(request.getRemark());
            items.add(item);
            index++;
        }
        return items;
    }

    private void applyTotals(PoOrder order, List<PoOrderItem> items) {
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PoOrderItem item : items) {
            totalQty = totalQty.add(item.getQty());
            totalAmount = totalAmount.add(item.getAmount());
        }
        order.setTotalQty(totalQty);
        order.setTotalAmount(MoneyUtils.scale2(totalAmount));
    }

    /**
     * 明细整体覆盖保存：先逻辑删除旧行，再写入新行
     */
    private void persistItems(Long orderId, List<PoOrderItem> items) {
        clearItems(orderId);
        for (PoOrderItem item : items) {
            item.setId(null);
            item.setOrderId(orderId);
            itemMapper.insert(item);
        }
    }

    /**
     * 全量替换明细：先逻辑删除旧明细（MyBatis-Plus 的 delete 会自动转成 deleted=1）
     */
    private void clearItems(Long orderId) {
        itemMapper.delete(new LambdaQueryWrapper<PoOrderItem>()
                .eq(PoOrderItem::getOrderId, orderId)
                .eq(PoOrderItem::getDeleted, 0));
    }

    /**
     * 生成订单编号：PO + yyyyMMdd + 4 位序列（Redis 不可用回退数据库计数）
     */
    private String generateOrderNo() {
        String date = LocalDate.now().format(DATE_PATTERN);
        for (int i = 0; i < 3; i++) {
            Long seq = cacheService.increment(RedisKeys.seqOrder(date), Duration.ofDays(1));
            if (seq == null) {
                long count = orderMapper.selectCount(new LambdaQueryWrapper<PoOrder>()
                        .likeRight(PoOrder::getOrderNo, "PO" + date));
                seq = count + 1;
            }
            String orderNo = "PO" + date + String.format("%04d", seq);
            if (orderMapper.selectCount(new LambdaQueryWrapper<PoOrder>().eq(PoOrder::getOrderNo, orderNo)) == 0) {
                return orderNo;
            }
        }
        // 兜底：时间戳序列
        return "PO" + date + String.format("%04d", System.currentTimeMillis() % 10000);
    }

    private List<OrderItemVO> loadItems(Long orderId) {
        List<PoOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<PoOrderItem>()
                .eq(PoOrderItem::getOrderId, orderId)
                .orderByAsc(PoOrderItem::getLineNo));
        List<OrderItemVO> result = new ArrayList<>();
        for (PoOrderItem item : items) {
            OrderItemVO vo = new OrderItemVO();
            vo.setId(item.getId());
            vo.setLineNo(item.getLineNo());
            vo.setMaterialId(item.getMaterialId());
            vo.setMaterialName(item.getMaterialName());
            vo.setSpec(item.getSpec());
            vo.setUnit(item.getUnit());
            vo.setQty(item.getQty());
            vo.setPrice(item.getPrice());
            vo.setTaxRate(item.getTaxRate());
            vo.setAmount(item.getAmount());
            vo.setRemark(item.getRemark());
            result.add(vo);
        }
        return result;
    }

    private List<ApprovalTaskVO> loadTasks(Long orderId) {
        List<PoApprovalTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PoApprovalTask>()
                .eq(PoApprovalTask::getOrderId, orderId)
                .orderByAsc(PoApprovalTask::getNodeSeq));
        List<ApprovalTaskVO> result = new ArrayList<>();
        for (PoApprovalTask task : tasks) {
            ApprovalTaskVO vo = new ApprovalTaskVO();
            vo.setId(task.getId());
            vo.setNodeSeq(task.getNodeSeq());
            vo.setNodeRole(task.getNodeRole());
            vo.setNodeName(task.getNodeName());
            vo.setApproverId(task.getApproverId());
            vo.setStatus(task.getStatus());
            vo.setApproveTime(task.getApproveTime());
            vo.setOpinion(task.getOpinion());
            if (task.getApproverId() != null) {
                SysUser approver = userMapper.selectById(task.getApproverId());
                vo.setApproverName(approver == null ? null : approver.getRealName());
            }
            result.add(vo);
        }
        return result;
    }

    private List<ApprovalLogVO> loadLogs(Long orderId) {
        List<PoApprovalLog> logs = logMapper.selectList(new LambdaQueryWrapper<PoApprovalLog>()
                .eq(PoApprovalLog::getOrderId, orderId)
                .orderByAsc(PoApprovalLog::getId));
        List<ApprovalLogVO> result = new ArrayList<>();
        for (PoApprovalLog log : logs) {
            ApprovalLogVO vo = new ApprovalLogVO();
            vo.setId(log.getId());
            vo.setNodeSeq(log.getNodeSeq());
            vo.setNodeRole(log.getNodeRole());
            vo.setAction(log.getAction());
            vo.setActionName(actionName(log.getAction()));
            vo.setFromStatus(log.getFromStatus());
            vo.setToStatus(log.getToStatus());
            vo.setOpinion(log.getOpinion());
            vo.setOperateTime(log.getOperateTime());
            if (log.getOperatorId() != null) {
                SysUser operator = userMapper.selectById(log.getOperatorId());
                vo.setOperatorName(operator == null ? null : operator.getRealName());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 计算当前用户可执行的动作（前端据此渲染按钮，不自行推断规则）
     */
    private List<String> resolveActions(PoOrder order, List<ApprovalTaskVO> tasks) {
        List<String> actions = new ArrayList<>();
        CurrentUser user = UserContext.required();
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status == null) {
            return actions;
        }
        boolean ownerOrAdmin = user.isAdmin() || user.getUserId().equals(order.getBuyerId());
        if (status.isEditable() && ownerOrAdmin) {
            actions.add("edit");
            actions.add("submit");
            actions.add("cancel");
            if (status == OrderStatus.DRAFT) {
                actions.add("delete");
            }
        }
        if (status == OrderStatus.APPROVING) {
            if (ownerOrAdmin) {
                actions.add("withdraw");
            }
            boolean canApprove = tasks.stream()
                    .anyMatch(task -> TaskStatus.PENDING.getCode().equals(task.getStatus())
                            && task.getNodeRole() != null
                            && task.getNodeRole().equals(user.getCurrentRole().getCode()));
            if (canApprove) {
                actions.add("approve");
                actions.add("reject");
            }
        }
        return actions;
    }

    private String actionName(String action) {
        for (ApprovalAction item : ApprovalAction.values()) {
            if (item.getCode().equals(action)) {
                return item.getName();
            }
        }
        return action;
    }

    private Map<Long, String> loadSupplierNames(List<PoOrder> orders) {
        Map<Long, String> names = new HashMap<>();
        List<Long> ids = orders.stream().map(PoOrder::getSupplierId).distinct().toList();
        if (ids.isEmpty()) {
            return names;
        }
        for (Supplier supplier : supplierMapper.selectBatchIds(ids)) {
            names.put(supplier.getId(), supplier.getSupplierName());
        }
        return names;
    }

    private Map<Long, String> loadUserNames(List<PoOrder> orders) {
        Map<Long, String> names = new HashMap<>();
        List<Long> ids = orders.stream().map(PoOrder::getBuyerId).distinct().toList();
        if (ids.isEmpty()) {
            return names;
        }
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            names.put(user.getId(), user.getRealName());
        }
        return names;
    }
}
