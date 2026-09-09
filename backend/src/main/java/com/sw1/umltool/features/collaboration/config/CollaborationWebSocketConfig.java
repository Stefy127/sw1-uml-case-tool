package com.sw1.umltool.features.collaboration.config;

import com.sw1.umltool.features.collaboration.websocket.CollaborationWebSocketHandler;
import com.sw1.umltool.features.collaboration.websocket.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class CollaborationWebSocketConfig implements WebSocketConfigurer {
    private final CollaborationWebSocketHandler handler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public CollaborationWebSocketConfig(CollaborationWebSocketHandler handler,
                                        JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.handler = handler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/collaboration")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("http://localhost:4200");
    }
}
