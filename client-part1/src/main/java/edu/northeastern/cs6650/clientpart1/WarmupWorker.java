package edu.northeastern.cs6650.clientpart1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WarmupWorker implements Runnable {

    private final String baseUrl; // e.g. ws://localhost:8080/chat/
    private final int roomId;
    private final int messagesToSend;
    private final Metrics metrics;

    private final ObjectMapper mapper = new ObjectMapper();

    public WarmupWorker(String baseUrl, int roomId, int messagesToSend, Metrics metrics) {
        this.baseUrl = baseUrl;
        this.roomId = roomId;
        this.messagesToSend = messagesToSend;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        WebSocketClient client = null;

        try {
            CountDownLatch doneLatch = new CountDownLatch(messagesToSend);

            client = new WebSocketClient(new URI(baseUrl + roomId)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    // no-op; connectBlocking handles waiting
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode node = mapper.readTree(message);
                        String status = node.has("status") ? node.get("status").asText() : "";
                        if ("SUCCESS".equals(status)) {
                            metrics.success.incrementAndGet();
                        } else {
                            metrics.failed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        metrics.failed.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    // Debug if you want:
                    // System.out.println("WARMUP CLOSE room=" + roomId + " code=" + code + " reason=" + reason);
                }

                @Override
                public void onError(Exception ex) {
                    // Debug if you want:
                    // System.out.println("WARMUP ERROR room=" + roomId + " ex=" + ex);
                }
            };

            // ✅ KEY FIX: blocking connect
            boolean ok = client.connectBlocking(5, TimeUnit.SECONDS);
            if (!ok) {
                metrics.failed.addAndGet(messagesToSend);
                return;
            }
            metrics.connections.incrementAndGet();

            for (int i = 0; i < messagesToSend; i++) {
                String payload = mapper.writeValueAsString(buildMsg(roomId));

                boolean sent = sendWithRetry(client, payload);
                if (!sent) {
                    // no response will arrive
                    metrics.failed.incrementAndGet();
                    doneLatch.countDown();
                }
            }

            // wait for all acks (or timeout)
            doneLatch.await(20, TimeUnit.SECONDS);

        } catch (Exception e) {
            metrics.failed.addAndGet(messagesToSend);
        } finally {
            try { if (client != null) client.close(); } catch (Exception ignored) {}
        }
    }

    private Map<String, Object> buildMsg(int roomId) {
        Map<String, Object> msg = new HashMap<>();
        int userId = 1 + (int)(Math.random() * 100000);
        msg.put("userId", userId);
        msg.put("username", "user" + userId);
        msg.put("message", "warmup hello room=" + roomId);
        msg.put("timestamp", Instant.now().toString());
        msg.put("messageType", "TEXT");
        return msg;
    }

    private boolean sendWithRetry(WebSocketClient client, String payload) {
        long backoff = 50;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                client.send(payload);
                return true;
            } catch (Exception e) {
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoff *= 2;
            }
        }
        return false;
    }
}
