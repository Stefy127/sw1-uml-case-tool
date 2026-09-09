package com.sw1.umltool.features.collaboration.websocket;

import com.sw1.umltool.features.auth.service.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtService jwt;

    public JwtHandshakeInterceptor(JwtService jwt) { this.jwt = jwt; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String token = queryParameter(uri.getRawQuery(), "token");
        String userId = token == null ? null : jwt.userId(token);
        if (userId == null || userId.isBlank()) return false;
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) { }

    private String queryParameter(String query, String wanted) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && wanted.equals(pair[0])) return java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }
}
