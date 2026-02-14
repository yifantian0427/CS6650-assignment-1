package edu.northeastern.cs6650.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // roomId -> sessions
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = safeRoomId(session);

        rooms.computeIfAbsent(roomId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = safeRoomId(session);

        try {
            ChatMessage chatMessage =
                    objectMapper.readValue(message.getPayload(), ChatMessage.class);

            String validationError = ChatValidator.validate(chatMessage);
            if (validationError != null) {
                sendError(session, validationError);
                return;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("serverTimestamp", Instant.now().toString());
            response.put("roomId", roomId);
            response.put("originalMessage", chatMessage);

            // Broadcast to all sessions in this room
            broadcastToRoom(roomId, response);

        } catch (Exception e) {
            sendError(session, "Invalid JSON format");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = safeRoomId(session);

        Set<WebSocketSession> sessions = rooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                rooms.remove(roomId);
            }
        }
    }

    private void broadcastToRoom(String roomId, Map<String, Object> response) throws Exception {
        String payload = objectMapper.writeValueAsString(response);

        Set<WebSocketSession> sessions = rooms.getOrDefault(roomId, Collections.emptySet());
        for (WebSocketSession s : sessions) {
            if (s != null && s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(payload));
                } catch (Exception ignored) {
                    // If a session is flaky, ignore; close cleanup happens in afterConnectionClosed
                }
            }
        }
    }

    private void sendError(WebSocketSession session, String error) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("serverTimestamp", Instant.now().toString());
        response.put("errorMessage", error);

        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    private String safeRoomId(WebSocketSession session) {
        String roomId = null;
        if (session != null) {
            Object val = session.getAttributes().get("roomId");
            if (val instanceof String) {
                roomId = (String) val;
            }
        }
        return (roomId == null || roomId.isBlank()) ? "default" : roomId;
    }
}
