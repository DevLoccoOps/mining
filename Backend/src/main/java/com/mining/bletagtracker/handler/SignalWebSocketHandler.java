package com.mining.bletagtracker.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mining.bletagtracker.entity.SignalReading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalWebSocketHandler extends TextWebSocketHandler {

    private static final ExecutorService broadcastExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> { Thread t = new Thread(r); t.setName("websocket-broadcast"); t.setDaemon(true); return t; }
    );

    private final ObjectMapper objectMapper;
    // ObjectWriter is thread-safe — created once, used concurrently without contention
    private final com.fasterxml.jackson.databind.ObjectWriter objectWriter = objectMapper.writer();
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client messages not expected, but keep session alive
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket closed: {}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.error("WebSocket error: {}", session.getId(), exception);
    }

    public void broadcastSignal(SignalReading signal) {
        // Non-blocking: delegate to a background thread so the REST request thread
        // is not held up by WebSocket serialization and network I/O.
        broadcastExecutor.submit(() -> {
            try {
                String json = objectWriter.writeValueAsString(signal);
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(new TextMessage(json));
                        } catch (Exception e) {
                            log.debug("Failed to broadcast to {}", session.getId());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to serialize signal for broadcast", e);
            }
        });
    }

    public int getConnectedCount() {
        return (int) sessions.stream().filter(WebSocketSession::isOpen).count();
    }
}
