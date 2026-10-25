package com.tamdaptit.rentalmanagement.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    /**
     * Cấu hình message broker.
     * /app  : prefix cho messages từ client gửi lên @MessageMapping
     * /user : prefix cho user-specific destinations (SimpMessagingTemplate.convertAndSendToUser)
     * /queue: simple broker cho user destinations
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker cho /queue (private) và /topic (broadcast nếu cần)
        registry.enableSimpleBroker("/queue", "/topic");

        // Prefix cho client gửi message
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix cho user-specific routing
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Đăng ký STOMP endpoint.
     * Frontend connect tới: ws://host/ws
     * Allowed origins lấy từ CORS config hiện tại (localhost:4200, 3000, 5173).
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Hỗ trợ cả raw WebSocket endpoint không qua SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    /**
     * Đăng ký WebSocket auth interceptor để xử lý JWT tại STOMP CONNECT.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
