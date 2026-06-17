package com.lunesnow.config;

import com.lunesnow.websocket.ChartWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ChartWebSocketHandler chartWebSocketHandler;

    @Override
    /**
     * 注册 WebSocket 处理器
     *
     * @param registry WebSocket 处理器注册表
     */
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                chartWebSocketHandler, // WebSocket 处理器
                "/ws/chart") // URL 路径
                .setAllowedOrigins("http://localhost:5173", "http://localhost:3000", "https://lunesnow.com");
    }
}
