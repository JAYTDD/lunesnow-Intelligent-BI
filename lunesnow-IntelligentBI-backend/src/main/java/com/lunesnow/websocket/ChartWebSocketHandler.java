package com.lunesnow.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图表 WebSocket 处理器
 * 管理用户连接，推送图表生成结果通知
 */
@Slf4j
@Component
public class ChartWebSocketHandler extends TextWebSocketHandler {

    /**
     * 用户 ID -> WebSocket Session 映射
     */
    private static final Map<Long, WebSocketSession> USER_SESSIONS = new ConcurrentHashMap<>();

    /**
     * Session ID -> 用户 ID 反向映射（用于清理）
     */
    private static final Map<String, Long> SESSION_USER_MAP = new ConcurrentHashMap<>();

    @Override
    /**
     * 处理 WebSocket 连接建立事件
     */
    public void afterConnectionEstablished(WebSocketSession session) {
        // 从 URL 参数获取 userId
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        Long userId = parseUserId(query);

        if (userId != null) {
            USER_SESSIONS.put(userId, session); // 存储用户会话信息
            SESSION_USER_MAP.put(session.getId(), userId);  // 存储会话信息
            log.info("WebSocket 连接建立: userId={}, sessionId={}", userId, session.getId());
        } else {
            log.warn("WebSocket 连接缺少 userId 参数: sessionId={}", session.getId());
        }
    }

    @Override
    /**
     * 处理 WebSocket 连接关闭事件
     */
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = SESSION_USER_MAP.remove(session.getId());
        if (userId != null) {
            USER_SESSIONS.remove(userId);
            log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
        }
    }

    /**
     * 处理 WebSocket 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端心跳响应
        String payload = message.getPayload();
        if ("ping".equals(payload)) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (IOException e) {
                log.error("WebSocket 心跳响应失败", e);
            }
        }
    }

    /**
     * 向指定用户推送消息
     *
     * @param userId  用户 ID
     * @param message JSON 消息
     */
    public void sendToUser(Long userId, String message) {
        WebSocketSession session = USER_SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                log.info("WebSocket 消息推送成功: userId={}, message={}", userId, message);
            } catch (IOException e) {
                log.error("WebSocket 消息推送失败: userId={}", userId, e);
            }
        } else {
            log.debug("WebSocket 用户不在线: userId={}", userId);
        }
    }

    /**
     * 推送图表生成成功通知
     */
    public void notifyChartSuccess(Long userId, Long chartId, String chartName) {
        String message = String.format(
                "{\"type\":\"success\",\"chartId\":%d,\"chartName\":\"%s\",\"message\":\"图表生成成功\"}",
                chartId, chartName != null ? chartName : "未知图表"
        );
        sendToUser(userId, message);
    }

    /**
     * 推送图表生成失败通知
     */
    public void notifyChartFailure(Long userId, Long chartId, String chartName, String reason) {
        String message = String.format(
                "{\"type\":\"failure\",\"chartId\":%d,\"chartName\":\"%s\",\"message\":\"图表生成失败: %s\"}",
                chartId, chartName != null ? chartName : "未知图表", reason != null ? reason : "未知原因"
        );
        sendToUser(userId, message);
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        WebSocketSession session = USER_SESSIONS.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 获取在线用户数
     */
    public int getOnlineCount() {
        return USER_SESSIONS.size();
    }

    /**
     * 从 URL query 解析 userId
     */
    private Long parseUserId(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && "userId".equals(kv[0])) {
                try {
                    return Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
