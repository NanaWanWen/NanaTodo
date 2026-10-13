package com.yonerp.po.agent.controller;

import com.yonerp.po.agent.dto.AgentAlertVO;
import com.yonerp.po.agent.dto.ChatRequest;
import com.yonerp.po.agent.dto.SuggestionVO;
import com.yonerp.po.agent.service.AgentService;
import com.yonerp.po.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
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
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
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
        SseEmitter emitter = new SseEmitter(120_000L); // 2 分钟超时

        sseExecutor.execute(() -> {
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
            }
        });

        return emitter;
    }

    /**
     * 获取当前未处理预警列表（前端每 30 秒轮询一次）
     */
    @GetMapping("/alerts")
    public Result<List<AgentAlertVO>> alerts() {
        return Result.ok(agentService.pendingAlerts());
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
}
