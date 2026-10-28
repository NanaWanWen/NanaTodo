package com.yonerp.po.agent.controller;

import com.yonerp.po.agent.dto.AgentAlertVO;
import com.yonerp.po.common.ErrorCode;
import com.yonerp.po.agent.dto.AlertActionVO;
import com.yonerp.po.agent.dto.ChatRequest;
import com.yonerp.po.agent.dto.SuggestionVO;
import com.yonerp.po.agent.entity.AgentAlertAction;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.agent.job.AlertScanJob;
import com.yonerp.po.agent.service.AgentService;
import com.yonerp.po.agent.service.AlertNotifyService;
import com.yonerp.po.agent.service.AlertRuleService;
import com.yonerp.po.common.Result;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI Agent 接口
 * <ul>
 *   <li>POST /api/agent/chat       SSE 流式对话（规则模式/LLM 模式自动切换）</li>
 *   <li>GET  /api/agent/alerts     轮询预警列表（30s 前端轮询）</li>
 *   <li>POST /api/agent/alerts/{id}/handle  标记预警已处理</li>
 *   <li>POST /api/agent/suggest/approval/{orderId}   辅助审批建议</li>
 *   <li>POST /api/agent/suggest/invoice/{invoiceId}  发票校验建议</li>
 *   <li>POST /api/agent/suggest/payment/{invoiceId}  付款建议</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AlertScanJob alertScanJob;
    private final AlertRuleService alertRuleService;
    private final AlertNotifyService alertNotifyService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-sse");
        t.setDaemon(true);
        return t;
    });

    /**
     * SSE 流式对话
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        CurrentUser currentUser = UserContext.required();
        SseEmitter emitter = new SseEmitter(120_000L); // 2 分钟超时

        sseExecutor.execute(() -> {
            UserContext.set(currentUser);
            try {
                agentService.chatStream(
                        request,
                        token -> {
                            try {
                                emitter.send(SseEmitter.event().name("token").data(token));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        (full, error) -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data(full));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> emitter.completeWithError(error)
                );
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                UserContext.clear();
            }
        });

        return emitter;
    }

    /**
     * 获取预警列表（前端每 30 秒轮询一次）
     *
     * @param handled 是否查看已处理预警，默认 false（未处理）
     * @param ignored 是否查看已忽略预警，默认 false（未忽略）
     */
    @GetMapping("/alerts")
    public Result<List<AgentAlertVO>> alerts(
            @RequestParam(defaultValue = "false") boolean handled,
            @RequestParam(defaultValue = "false") boolean ignored) {
        return Result.ok(agentService.listAlerts(handled, ignored));
    }

    /**
     * 标记预警已处理
     */
    @PostMapping("/alerts/{alertId}/handle")
    public Result<Void> handleAlert(@PathVariable String alertId) {
        agentService.markAlertHandled(alertId);
        return Result.ok();
    }

    /**
     * 忽略预警（不再显示，但保留历史）
     */
    @PostMapping("/alerts/{alertId}/ignore")
    public Result<Void> ignoreAlert(@PathVariable String alertId) {
        agentService.ignoreAlert(alertId);
        return Result.ok();
    }

    /**
     * 恢复已忽略的预警
     */
    @PostMapping("/alerts/{alertId}/restore")
    public Result<Void> restoreAlert(@PathVariable String alertId) {
        agentService.restoreAlert(alertId);
        return Result.ok();
    }

    /**
     * 手动触发一次预警扫描（用于演示或调试）
     */
    @PostMapping("/alerts/scan")
    public Result<Integer> scanAlerts() {
        int count = alertScanJob.scan();
        return Result.ok(count);
    }

    /**
     * 辅助审批建议（订单提交时调用，生成审批意见草稿）
     */
    @PostMapping("/suggest/approval/{orderId}")
    public Result<SuggestionVO> suggestApproval(@PathVariable Long orderId,
                                                @RequestParam(required = false) String context) {
        return Result.ok(agentService.suggestApproval(orderId, context));
    }

    /**
     * 发票校验建议
     */
    @PostMapping("/suggest/invoice/{invoiceId}")
    public Result<SuggestionVO> suggestInvoiceCheck(@PathVariable Long invoiceId,
                                                    @RequestParam(required = false) String context) {
        return Result.ok(agentService.suggestInvoiceCheck(invoiceId, context));
    }

    /**
     * 付款建议
     */
    @PostMapping("/suggest/payment/{invoiceId}")
    public Result<SuggestionVO> suggestPayment(@PathVariable Long invoiceId,
                                               @RequestParam(required = false) String context) {
        return Result.ok(agentService.suggestPayment(invoiceId, context));
    }

    // ==================== 预警规则配置 ====================

    @GetMapping("/alert-rules")
    public Result<List<AgentAlertRule>> listAlertRules() {
        return Result.ok(alertRuleService.listAll());
    }

    @PostMapping("/alert-rules")
    public Result<AgentAlertRule> createAlertRule(@RequestBody AgentAlertRule rule) {
        return Result.ok(alertRuleService.create(rule));
    }

    @PutMapping("/alert-rules/{id}")
    public Result<AgentAlertRule> updateAlertRule(@PathVariable Long id,
                                                  @RequestBody AgentAlertRule rule) {
        return Result.ok(alertRuleService.update(id, rule));
    }

    @DeleteMapping("/alert-rules/{id}")
    public Result<Void> deleteAlertRule(@PathVariable Long id) {
        alertRuleService.delete(id);
        return Result.ok();
    }

    // ==================== 预警处理闭环 ====================

    /**
     * 标记预警已读
     */
    @PostMapping("/alerts/{alertId}/read")
    public Result<Void> readAlert(@PathVariable String alertId) {
        agentService.markAlertRead(alertId);
        return Result.ok();
    }

    /**
     * 指派预警给指定人
     */
    @PostMapping("/alerts/{alertId}/assign")
    public Result<Void> assignAlert(@PathVariable String alertId,
                                    @RequestParam(required = false) Long assigneeId,
                                    @RequestParam(required = false) String assignee,
                                    @RequestParam(required = false) String note) {
        agentService.assignAlert(alertId, assigneeId, assignee, note);
        return Result.ok();
    }

    /**
     * 转办预警给指定人
     */
    @PostMapping("/alerts/{alertId}/transfer")
    public Result<Void> transferAlert(@PathVariable String alertId,
                                      @RequestParam(required = false) Long assigneeId,
                                      @RequestParam(required = false) String assignee,
                                      @RequestParam(required = false) String note) {
        agentService.transferAlert(alertId, assigneeId, assignee, note);
        return Result.ok();
    }

    /**
     * 处理预警（标记已处理 + 处理意见）
     */
    @PostMapping("/alerts/{alertId}/process")
    public Result<Void> processAlert(@PathVariable String alertId,
                                     @RequestParam(required = false) String note) {
        agentService.processAlert(alertId, note);
        return Result.ok();
    }

    /**
     * 预警处理流水（审计时间线）
     */
    @GetMapping("/alerts/{alertId}/actions")
    public Result<List<AlertActionVO>> getAlertActions(@PathVariable String alertId) {
        return Result.ok(agentService.getAlertActions(alertId));
    }

    // ==================== SSE 实时推送 ====================

    /**
     * 站内未读预警计数（顶栏 Badge 用）
     */
    @GetMapping("/alerts/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(agentService.unreadCount());
    }

    /**
     * SSE 实时推送（替代 30s 轮询，前端监听此接口）
     *
     * <p>由于 SSE 无法携带自定义请求头，这里允许通过 query 参数传 userId，
     * 由 AuthInterceptor 回退读取 header，若仍失败则不允许空连接。</p>
     */
    @GetMapping(value = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter alertStream(@RequestParam(required = false) Long userId) {
        if (userId == null) {
            throw new com.yonerp.po.common.BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份");
        }
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        alertNotifyService.register(userId, emitter);

        sseExecutor.execute(() -> {
            try {
                // 初始化：推送当前未读计数
                long count = agentService.unreadCount();
                emitter.send(SseEmitter.event()
                        .name("unread-count").data(count));
            } catch (IOException e) {
                log.debug("SSE init unread-count push failed for userId={}", userId, e);
            }
        });

        return emitter;
    }
}
