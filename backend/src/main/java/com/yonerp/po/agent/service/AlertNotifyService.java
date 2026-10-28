package com.yonerp.po.agent.service;

import com.yonerp.po.agent.dto.AgentAlertVO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 预警通知服务（站内红点 + 邮件 + SSE 实时推送）
 * <p>
 * 可靠性设计：
 * <ul>
 *   <li>同一用户可持有多个 SSE 连接（多标签页），避免新连接覆盖旧连接导致消息丢失</li>
 *   <li>定时心跳保活，避免连接被服务端超时断开（心跳会刷新 SseEmitter 的发送时间戳）</li>
 *   <li>推送失败仅移除失效连接，不影响其他连接</li>
 * </ul>
 */
@Slf4j
@Service
public class AlertNotifyService {

    /** userId -> 该用户的全部 SSE 连接（支持多标签页同时在线） */
    private static final Map<Long, CopyOnWriteArraySet<SseEmitter>> SSE_CONNECTIONS = new ConcurrentHashMap<>();

    /** 心跳间隔（毫秒）：早于 SseEmitter 超时（300s），避免被动断开 */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "alert-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stopHeartbeat() {
        heartbeatExecutor.shutdownNow();
    }

    /**
     * 注册 SSE 连接（同一用户可注册多个，互不覆盖）
     */
    public void register(Long userId, SseEmitter emitter) {
        SSE_CONNECTIONS.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = SSE_CONNECTIONS.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            SSE_CONNECTIONS.remove(userId);
        }
    }

    /**
     * 心跳：发送注释帧，刷新连接的发送时间戳，避免超时断开
     */
    private void sendHeartbeat() {
        if (SSE_CONNECTIONS.isEmpty()) {
            return;
        }
        SSE_CONNECTIONS.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (Exception e) {
                    log.debug("SSE heartbeat failed, remove connection: userId={}", userId);
                    emitters.remove(emitter);
                }
            }
            if (emitters.isEmpty()) {
                SSE_CONNECTIONS.remove(userId);
            }
        });
    }

    /**
     * 向指定用户的所有连接推送预警创建事件（前端监听 'alert' 事件后自动刷新列表）
     */
    public void pushAlertCreated(Long userId, AgentAlertVO alert) {
        Set<SseEmitter> emitters = SSE_CONNECTIONS.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No SSE connection for userId={}, skip push", userId);
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("alert").data(alert.getAlertId()));
                log.info("SSE push alert: userId={} alertId={}", userId, alert.getAlertId());
            } catch (IOException e) {
                log.warn("SSE push failed, removing connection: userId={}", userId, e);
                emitters.remove(emitter);
            }
        }
        if (emitters.isEmpty()) {
            SSE_CONNECTIONS.remove(userId);
        }
    }

    /**
     * 向所有在线连接广播预警创建事件
     *
     * <p>定时扫描无具体用户上下文，故广播给全部在线连接；
     * 前端收到 'alert' 事件后自动重新拉取列表，实现实时自动刷新。</p>
     */
    public void broadcastAlertCreated(AgentAlertVO alert) {
        if (SSE_CONNECTIONS.isEmpty()) {
            return;
        }
        int delivered = 0;
        for (Map.Entry<Long, CopyOnWriteArraySet<SseEmitter>> entry : SSE_CONNECTIONS.entrySet()) {
            Set<SseEmitter> emitters = entry.getValue();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("alert").data(alert.getAlertId()));
                    delivered++;
                } catch (IOException e) {
                    log.warn("Broadcast alert failed for userId={}", entry.getKey(), e);
                    emitters.remove(emitter);
                }
            }
            if (emitters.isEmpty()) {
                SSE_CONNECTIONS.remove(entry.getKey());
            }
        }
        log.info("SSE broadcast alert: id={} delivered={}", alert.getAlertId(), delivered);
    }

    /**
     * 向所有在线连接广播未读计数
     */
    public void broadcastUnreadCount(long count) {
        if (SSE_CONNECTIONS.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, CopyOnWriteArraySet<SseEmitter>> entry : SSE_CONNECTIONS.entrySet()) {
            Set<SseEmitter> emitters = entry.getValue();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("unread-count").data(count));
                } catch (IOException e) {
                    log.warn("Broadcast unread-count failed for userId={}", entry.getKey(), e);
                    emitters.remove(emitter);
                }
            }
            if (emitters.isEmpty()) {
                SSE_CONNECTIONS.remove(entry.getKey());
            }
        }
    }

    /**
     * 推送预警通知（站内 + SSE；邮件渠道预留）
     */
    public void notify(AgentAlertVO alert) {
        log.info("Alert notified: id={} type={}", alert.getAlertId(), alert.getAlertType());
        // 站内未读由 alert.readFlag=0 体现，实时刷新由 SSE 广播驱动
        broadcastAlertCreated(alert);
        // TODO: 邮件渠道按规则 notifyChannel/notifyRoles 推送
    }
}
