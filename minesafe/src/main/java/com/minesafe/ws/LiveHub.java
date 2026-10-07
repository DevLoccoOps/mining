package com.minesafe.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds open WebSocket sessions and fans out the aggregated {@code state_update}
 * payload. The broadcaster (M3) calls {@link #broadcast(Object)} on a 500 ms tick.
 */
@Component
public class LiveHub extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveHub.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public LiveHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Live client connected: {} (total={})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Live client disconnected: {} status={} (total={})",
                session.getId(), status, sessions.size());
    }

    public int activeSessions() {
        return sessions.size();
    }

    public void broadcast(Object payload) {
        if (sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize state payload: {}", e.getMessage());
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (Exception e) {
                log.warn("Send to {} failed: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }
}
