package edu.northeastern.cs6650.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs6650.server.dto.QueueMessage;
import edu.northeastern.cs6650.server.queue.QueuePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Receives chat messages, validates, publishes to queue, and sends ack/error back to sender.
 * Does not broadcast; the consumer application distributes to room participants.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QueuePublisher queuePublisher;
    private final RoomSessionRegistry roomSessions;

    @Value("${app.server.id:server-1}")
    private String serverId;

    public ChatWebSocketHandler(QueuePublisher queuePublisher, RoomSessionRegistry roomSessions) {
        this.queuePublisher = queuePublisher;
        this.roomSessions = roomSessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = safeRoomId(session);
        roomSessions.addSession(roomId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = safeRoomId(session);
        roomSessions.removeSession(roomId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = safeRoomId(session);
        String payload = message.getPayload();

        try {
            if (payload.trim().startsWith("[")) {
                // Batch processing
                java.util.List<ChatMessage> batch = objectMapper.readValue(payload, 
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, ChatMessage.class));
                
                java.util.List<QueueMessage> queueMsgs = new java.util.ArrayList<>();
                String ip = (session.getRemoteAddress() != null) ? session.getRemoteAddress().toString() : "unknown";
                for (ChatMessage chatMessage : batch) {
                    String validationError = ChatValidator.validate(chatMessage);
                    if (validationError != null) {
                        sendError(session, "Validation failed in batch: " + validationError);
                        return;
                    }
                    queueMsgs.add(convertToQueueMessage(chatMessage, roomId, ip));
                }

                if (!queuePublisher.publishBatch(queueMsgs)) {
                    sendError(session, "Queue unavailable (Batch)");
                } else {
                    sendAck(session, roomId, "BATCH_SUCCESS", batch.size()); // Reverted to sendAck to maintain existing method signature
                }
            } else {
                // Single message
                ChatMessage chatMsg = objectMapper.readValue(payload, ChatMessage.class);
                String validationError = ChatValidator.validate(chatMsg);
                if (validationError != null) {
                    sendError(session, validationError);
                    return;
                }
                String ip = (session.getRemoteAddress() != null) ? session.getRemoteAddress().toString() : "unknown";
                QueueMessage queueMsg = convertToQueueMessage(chatMsg, roomId, ip); // Corrected variable name from qMsg to queueMsg and chatMessage to chatMsg
                boolean published = queuePublisher.publish(queueMsg);

                if (published) {
                    sendAck(session, roomId, "SUCCESS", 1);
                } else {
                    sendError(session, "Queue unavailable");
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing message from room " + roomId + ": " + e.getMessage());
            e.printStackTrace();
            sendError(session, "Invalid format: " + e.getMessage());
        }
    }

    private QueueMessage convertToQueueMessage(ChatMessage chatMessage, String roomId, String ip) {
        QueueMessage queueMsg = new QueueMessage();
        queueMsg.messageId = UUID.randomUUID().toString();
        queueMsg.roomId = roomId;
        queueMsg.userId = String.valueOf(chatMessage.userId);
        queueMsg.username = chatMessage.username;
        queueMsg.message = chatMessage.message;
        queueMsg.timestamp = chatMessage.timestamp;
        queueMsg.messageType = chatMessage.messageType;
        queueMsg.serverId = serverId;
        queueMsg.clientIp = ip;
        return queueMsg;
    }

    private void sendAck(WebSocketSession session, String roomId, String status, int count) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("serverTimestamp", Instant.now().toString());
        response.put("roomId", roomId);
        response.put("count", count);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
        }
    }

    private void sendError(WebSocketSession session, String error) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("serverTimestamp", Instant.now().toString());
        response.put("errorMessage", error);

        if (session != null) {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            }
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
