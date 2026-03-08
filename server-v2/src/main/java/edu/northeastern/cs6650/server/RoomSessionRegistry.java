package edu.northeastern.cs6650.server;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of roomId -> WebSocket sessions for broadcast.
 * Used by internal broadcast endpoint (consumer calls it to distribute messages).
 */
@Component
public class RoomSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public void addSession(String roomId, WebSocketSession session) {
        rooms.computeIfAbsent(roomId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(session);
    }

    public void removeSession(String roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = rooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                rooms.remove(roomId);
            }
        }
    }

    /** Broadcast payload (JSON string) to all sessions in the room. */
    public void broadcast(String roomId, String payload) {
        Set<WebSocketSession> sessions = rooms.getOrDefault(roomId, Collections.emptySet());
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession s : sessions) {
            if (s != null && s.isOpen()) {
                try {
                    s.sendMessage(message);
                } catch (Exception ignored) {
                    // Session may be closed; cleanup happens in handler
                }
            }
        }
    }
}
