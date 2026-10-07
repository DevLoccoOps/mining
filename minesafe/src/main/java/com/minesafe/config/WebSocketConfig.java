package com.minesafe.config;

import com.minesafe.ws.LiveHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveHub liveHub;
    private final String[] allowedOrigins;

    public WebSocketConfig(LiveHub liveHub,
                           @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.liveHub = liveHub;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveHub, "/ws/state").setAllowedOrigins(allowedOrigins);
    }
}