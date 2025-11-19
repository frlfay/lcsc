package com.lcsc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类
 * 用于爬虫实时进度推送和任务状态更新
 * 
 * @author lcsc-crawler
 * @since 2025-09-07
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的消息代理，并设置消息代理的前缀
        config.enableSimpleBroker("/topic", "/queue");
        
        // 设置应用程序的目标前缀，客户端发送消息时使用
        config.setApplicationDestinationPrefixes("/app");
        
        // 设置用户目标前缀，用于发送个人消息
        config.setUserDestinationPrefix("/user");
    }

    /**
     * 注册STOMP端点
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册一个STOMP端点，客户端使用此端点连接到WebSocket服务器
        registry.addEndpoint("/ws/crawler")
                .setAllowedOriginPatterns("*")  // 允许跨域
                .withSockJS();  // 启用SockJS后备选项
        
        // 添加一个不使用SockJS的纯WebSocket端点
        registry.addEndpoint("/ws/crawler-native")
                .setAllowedOriginPatterns("*");
    }
}