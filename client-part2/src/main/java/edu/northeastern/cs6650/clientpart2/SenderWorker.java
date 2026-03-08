package edu.northeastern.cs6650.clientpart2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SenderWorker implements Runnable {

    private final String baseUrl; // e.g. ws://localhost:8080/chat
    private final int roomId;      // FIXED room for this worker

    private final BlockingQueue<ChatMessage> roomQueue;
    private final AtomicBoolean doneProducing;
    private final LatencyTracker latencyTracker;

    private final ObjectMapper mapper = new ObjectMapper();
    private final long ackTimeoutMs = 3000;

    /** Only log first few server errors to stderr to avoid flood */
    private static final AtomicInteger serverErrorLogCount = new AtomicInteger(0);
    private static final int MAX_SERVER_ERROR_LOGS = 3;

    public SenderWorker(String baseUrl,
                        int roomId,
                        BlockingQueue<ChatMessage> roomQueue,
                        AtomicBoolean doneProducing,
                        LatencyTracker latencyTracker) {
        this.baseUrl = baseUrl;
        this.roomId = roomId;
        this.roomQueue = roomQueue;
        this.doneProducing = doneProducing;
        this.latencyTracker = latencyTracker;
    }

    @Override
    public void run() {
        final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        WebSocketClient client = null;

        try {
            String url = baseUrl.endsWith("/")
                    ? (baseUrl + roomId)
                    : (baseUrl + "/" + roomId);

            client = new WebSocketClient(new URI(url)) {
                @Override public void onOpen(ServerHandshake handshakedata) { }
                @Override public void onMessage(String message) { inbox.offer(message); }
                @Override public void onClose(int code, String reason, boolean remote) { }
                @Override public void onError(Exception ex) { }
            };

            client.connectBlocking();

            while (true) {
                ChatMessage msg = roomQueue.poll(200, TimeUnit.MILLISECONDS);
                if (msg == null) {
                    if (doneProducing.get() && roomQueue.isEmpty()) break;
                    continue;
                }

                String payload = mapper.writeValueAsString(msg);
                String messageType = safeType(msg);

                long startMs = System.currentTimeMillis();
                client.send(payload);

                String ack = inbox.poll(ackTimeoutMs, TimeUnit.MILLISECONDS);
                long endMs = System.currentTimeMillis();
                long latency = endMs - startMs;

                if (ack == null) {
                    latencyTracker.recordTimeout(messageType, roomId);
                    continue;
                }

                if (isSuccessAck(ack)) {
                    latencyTracker.recordSuccess(messageType, latency, roomId);
                } else {
                    logServerError(ack, roomId);
                    latencyTracker.recordServerError(messageType, latency, roomId);
                }
            }

        } catch (Exception e) {
            latencyTracker.recordClientError("UNKNOWN", roomId);
        } finally {
            try { if (client != null) client.close(); } catch (Exception ignored) { }
        }
    }

    private String safeType(ChatMessage msg) {
        if (msg == null) return "UNKNOWN";
        if (msg.messageType == null || msg.messageType.isBlank()) return "UNKNOWN";
        return msg.messageType;
    }

    private boolean isSuccessAck(String ackJson) {
        try {
            JsonNode root = mapper.readTree(ackJson);
            String status = root.path("status").asText("");
            return "SUCCESS".equalsIgnoreCase(status);
        } catch (Exception e) {
            return false;
        }
    }

    private void logServerError(String ackJson, int roomId) {
        if (serverErrorLogCount.incrementAndGet() > MAX_SERVER_ERROR_LOGS) return;
        try {
            JsonNode root = mapper.readTree(ackJson);
            String errorMsg = root.has("errorMessage") ? root.get("errorMessage").asText() : "(no errorMessage)";
            System.err.println("[Server ERROR] roomId=" + roomId + " | errorMessage=" + errorMsg);
            if (serverErrorLogCount.get() == 1) {
                System.err.println("[Server ERROR] full response: " + ackJson);
            }
        } catch (Exception e) {
            System.err.println("[Server ERROR] roomId=" + roomId + " | raw: " + ackJson);
        }
    }
}
