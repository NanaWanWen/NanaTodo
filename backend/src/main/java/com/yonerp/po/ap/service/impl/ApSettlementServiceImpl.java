package com.yonerp.po.ap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.ap.dto.ApQuery;
import com.yonerp.po.ap.dto.ForceSettleCmd;
import com.yonerp.po.ap.dto.ManualSettleCmd;
import com.yonerp.po.ap.dto.ResolveCmd;
import com.yonerp.po.ap.entity.PoApSettlement;
import com.yonerp.po.ap.mapper.ApSettlementMapper;
import com.yonerp.po.ap.service.ApSettlementService;
import com.yonerp.po.ap.service.MatchEngine;
import com.yonerp.po.ap.vo.ApSettlementListVO;
import com.yonerp.po.ap.vo.SupplierBalanceVO;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.entity.SysUser;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.base.mapper.SysUserMapper;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.PageResult;
import com.yonerp.po.invoice.entity.PoInvoice;
import com.yonerp.po.invoice.mapper.InvoiceMapper;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import com.yonerp.po.payment.entity.PoPayment;
import com.yonerp.po.payment.mapper.PaymentMapper;
import com.yonerp.po.receipt.entity.PoReceipt;
import com.yonerp.po.receipt.mapper.ReceiptMapper;
import com.yonerp.po.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 应付对账 / 三单匹配服务实现（用友式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApSettlementServiceImpl implements ApSettlementService {

    private final ApSettlementMapper settlementMapper;
    private final MatchEngine matchEngine;
    private final PoOrderMapper orderMapper;
    private final ReceiptMapper receiptMapper;
    private final InvoiceMapper invoiceMapper;
    private final PaymentMapper paymentMapper;
    private final SupplierMapper supplierMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public PageResult<ApSettlementListVO> page(ApQuery query) {
        LambdaQueryWrapper<PoApSettlement> wrapper = new LambdaQueryWrapper<>();
        if (query.getOrderId() != null) {
            wrapper.eq(PoApSettlement::getOrderId, query.getOrderId());
        }
        if (query.getMatchStatus() != null && !query.getMatchStatus().isBlank()) {
            // 支持逗号分隔多状态（差异工作台一次查 HOLD/PARTIAL_QTY/PARTIAL_AMOUNT）
            String[] statuses = java.util.Arrays.stream(query.getMatchStatus().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
            if (statuses.length > 1) {
                wrapper.in(PoApSettlement::getMatchStatus, statuses);
            } else if (statuses.length == 1) {
                wrapper.eq(PoApSettlement::getMatchStatus, statuses[0]);
            }
        }
        if (query.getMatchType() != null && !query.getMatchType().isBlank()) {
            wrapper.eq(PoApSettlement::getMatchType, query.getMatchType());
        }
        if (query.getDiffType() != null && !query.getDiffType().isBlank()) {
            wrapper.eq(PoApSettlement::getDiffType, query.getDiffType());
        }
        if (query.getSupplierId() != null) {
            wrapper.eq(PoApSettlement::getSupplierId, query.getSupplierId());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(PoApSettlement::getMatchNo, query.getKeyword().trim());
        }
        wrapper.orderByDesc(PoApSettlement::getUpdateTime);

        var page = settlementMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(query.getPageNo(), query.getPageSize()),
            wrapper
        );
        return PageResult.of(page, this::toListVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void match(Long orderId) {
        matchEngine.matchByOrder(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PoApSettlement> autoSettle(List<Long> orderIds) {
        return matchEngine.matchBatch(orderIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoApSettlement manualSettle(ManualSettleCmd cmd) {
        PoOrder order = orderMapper.selectById(cmd.getOrderId());
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }

        // MatchEngine 幂等执行：已有结算单则原地重算并标记为手工结算，不会产生重复结算单
        PoApSettlement settlement = matchEngine.matchByOrder(cmd.getOrderId());
        if (settlement == null) {
            throw BizException.param("三单匹配失败：缺少收货单或发票");
        }

        // 标记为手工结算并留痕
        settlement.setMatchType("MANUAL");
        settlement.setMatchedBy(UserContext.currentUserId());
        settlement.setMatchedTime(LocalDateTime.now());
        if (cmd.getRemark() != null && !cmd.getRemark().isBlank()) {
            settlement.setRemark(cmd.getRemark());
        }
        settlementMapper.updateById(settlement);

        log.info("手工结算完成：matchNo={}, orderId={}", settlement.getMatchNo(), cmd.getOrderId());
        return settlement;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceSettle(Long settlementId, ForceSettleCmd cmd) {
        PoApSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw BizException.notFound("结算单不存在");
        }

        // 只有 HOLD 或 PARTIAL 状态可以强制匹配
        String status = settlement.getMatchStatus();
        if (!"HOLD".equals(status) && !"PARTIAL_QTY".equals(status) && !"PARTIAL_AMOUNT".equals(status)) {
            throw BizException.statusNotAllow("当前状态不允许强制匹配");
        }

        settlement.setMatchStatus("MATCHED");
        settlement.setMatchType("FORCE");
        settlement.setHoldReason(cmd.getReason());
        settlement.setResolution(cmd.getResolution());
        settlement.setMatchedBy(UserContext.currentUserId());
        settlement.setMatchedTime(LocalDateTime.now());

        settlementMapper.updateById(settlement);

        log.info("强制匹配完成：matchNo={}, reason={}", settlement.getMatchNo(), cmd.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveDiff(Long settlementId, ResolveCmd cmd) {
        PoApSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw BizException.notFound("结算单不存在");
        }

        String status = settlement.getMatchStatus();
        if (!"HOLD".equals(status) && !"PARTIAL_QTY".equals(status) && !"PARTIAL_AMOUNT".equals(status)) {
            throw BizException.statusNotAllow("当前状态不允许差异处理");
        }

        // 根据处理方式更新状态（动作码与前端一致：ADD_RECEIPT/REDO_INVOICE/ADJUST_PRICE/ALLOW_LOSS/FORCE_MATCH）
        // 异步动作（补收货/退票重开/调价）进入对应"等待态"：差异未解决前继续拦住付款，
        // 但工作台与预警都能识别"已有人在跟进"，不再当无人处理的差异反复刷预警；
        // 后续新收货/新发票/订单调价触发重新匹配时，状态会被自动推回正常流程。
        String resolution = cmd.getResolution();
        switch (resolution) {
            case "ADD_RECEIPT":
                settlement.setMatchStatus("WAITING_RECEIPT");
                settlement.setHoldReason(buildWaitingReason("补收货", "等待供应商补货并补录收货单", cmd.getRemark()));
                break;
            case "REDO_INVOICE":
                settlement.setMatchStatus("WAITING_INVOICE");
                settlement.setHoldReason(buildWaitingReason("退票重开", "等待供应商作废原票并重新开具", cmd.getRemark()));
                break;
            case "ADJUST_PRICE":
                settlement.setMatchStatus("WAITING_ORDER");
                settlement.setHoldReason(buildWaitingReason("调价", "等待订单/合同单价调整后重新匹配", cmd.getRemark()));
                break;
            case "ALLOW_LOSS":
                // 合理损耗放行，差异进损耗，直接标记为匹配
                settlement.setMatchStatus("MATCHED");
                settlement.setResolution(resolution);
                settlement.setMatchedBy(UserContext.currentUserId());
                settlement.setMatchedTime(LocalDateTime.now());
                break;
            case "FORCE_MATCH":
                // 领导审批强制匹配：记录处理方式，实际置位走 forceSettle 审批流
                settlement.setResolution(resolution);
                settlement.setHoldReason(cmd.getRemark());
                break;
            default:
                throw BizException.param("不支持的处理方式：" + resolution);
        }

        // 异步动作统一记录处理人与处理时间，供工作台"处理中"分组与审计时间线展示
        if (!"ALLOW_LOSS".equals(resolution)) {
            settlement.setResolution(resolution);
            settlement.setMatchedBy(UserContext.currentUserId());
            settlement.setMatchedTime(LocalDateTime.now());
        }

        settlementMapper.updateById(settlement);

        log.info("差异处理完成：matchNo={}, resolution={}, status={}",
            settlement.getMatchNo(), resolution, settlement.getMatchStatus());
    }

    /**
     * 组装等待态挂起原因：已登记【动作】，等待什么｜登记人｜登记时间｜处理意见
     */
    private String buildWaitingReason(String actionLabel, String waitingFor, String remark) {
        return String.format("已登记【%s】，%s｜登记人：%s｜登记时间：%s｜处理意见：%s",
            actionLabel, waitingFor,
            UserContext.currentUserId(),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            remark);
    }

    @Override
    public List<SupplierBalanceVO> supplierBalances() {
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getDeleted, 0));
        List<SupplierBalanceVO> result = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            SupplierBalanceVO vo = new SupplierBalanceVO();
            vo.setSupplierId(supplier.getId());
            vo.setSupplierName(supplier.getSupplierName());

            List<PoInvoice> unpaid = invoiceMapper.selectList(new LambdaQueryWrapper<PoInvoice>()
                    .eq(PoInvoice::getSupplierId, supplier.getId())
                    .in(PoInvoice::getInvoiceStatus, "UNCHECKED", "CHECKED"));
            BigDecimal unpaidAmount = BigDecimal.ZERO;
            for (PoInvoice invoice : unpaid) {
                unpaidAmount = unpaidAmount.add(invoice.getTotalAmount());
            }
            vo.setUnpaidAmount(unpaidAmount);
            vo.setUnpaidInvoiceCount(unpaid.size());

            List<PoPayment> paid = paymentMapper.selectList(new LambdaQueryWrapper<PoPayment>()
                    .eq(PoPayment::getSupplierId, supplier.getId())
                    .eq(PoPayment::getStatus, "PAID"));
            BigDecimal paidAmount = BigDecimal.ZERO;
            for (PoPayment payment : paid) {
                paidAmount = paidAmount.add(payment.getPaymentAmount());
            }
            vo.setPaidAmount(paidAmount);
            vo.setBalance(unpaidAmount.subtract(paidAmount));

            result.add(vo);
        }
        return result;
    }

    // ==================== 私有方法 ====================

    private ApSettlementListVO toListVO(PoApSettlement s) {
        ApSettlementListVO vo = new ApSettlementListVO();
        vo.setId(s.getId());
        vo.setMatchNo(s.getMatchNo());
        vo.setOrderId(s.getOrderId());
        vo.setReceiptId(s.getReceiptId());
        vo.setInvoiceId(s.getInvoiceId());
        vo.setSupplierId(s.getSupplierId());
        vo.setOrderAmount(s.getOrderAmount());
        vo.setReceiptAmount(s.getReceiptAmount());
        vo.setInvoiceAmount(s.getInvoiceAmount());
        vo.setDiffAmount(s.getDiffAmount());
        vo.setQtyDiff(s.getQtyDiff());
        vo.setPriceDiff(s.getPriceDiff());
        vo.setTaxDiff(s.getTaxDiff());
        vo.setDiffType(s.getDiffType());
        vo.setMatchStatus(s.getMatchStatus());
        vo.setMatchType(s.getMatchType());
        vo.setMatchRuleCode(s.getMatchRuleCode());
        vo.setCurrency(s.getCurrency());
        vo.setTolerance(s.getTolerance());
        vo.setMatchedBy(s.getMatchedBy());
        vo.setMatchedTime(s.getMatchedTime());
        vo.setHoldReason(s.getHoldReason());
        vo.setResolution(s.getResolution());
        vo.setRemark(s.getRemark());
        vo.setUpdateTime(s.getUpdateTime());

        PoOrder order = s.getOrderId() != null ? orderMapper.selectById(s.getOrderId()) : null;
        vo.setOrderNo(order == null ? null : order.getOrderNo());

        PoReceipt receipt = s.getReceiptId() != null ? receiptMapper.selectById(s.getReceiptId()) : null;
        vo.setReceiptNo(receipt == null ? null : receipt.getReceiptNo());

        PoInvoice invoice = s.getInvoiceId() != null ? invoiceMapper.selectById(s.getInvoiceId()) : null;
        vo.setInvoiceNo(invoice == null ? null : invoice.getInvoiceNo());

        Supplier supplier = s.getSupplierId() != null ? supplierMapper.selectById(s.getSupplierId()) : null;
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());

        if (s.getMatchedBy() != null) {
            SysUser user = sysUserMapper.selectById(s.getMatchedBy());
            if (user != null) {
                vo.setMatchedByName(user.getRealName() != null && !user.getRealName().isBlank()
                        ? user.getRealName()
                        : user.getUsername());
            }
        }

        return vo;
    }
}
