package edu.northeastern.cs6650.clientpart1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SenderWorker implements Runnable {

    private final String baseUrl; // ws://localhost:8080/chat/
    private final BlockingQueue<ChatMessage> queue;
    private final Metrics metrics;
    private final AtomicBoolean doneProducing;

    private final ObjectMapper mapper = new ObjectMapper();

    public SenderWorker(String baseUrl,
                        BlockingQueue<ChatMessage> queue,
                        Metrics metrics,
                        AtomicBoolean doneProducing) {
        this.baseUrl = baseUrl;
        this.queue = queue;
        this.metrics = metrics;
        this.doneProducing = doneProducing;
    }

    @Override
    public void run() {
        WebSocketClient client = null;
        Integer fixedRoomId = null;

        try {
            // Each worker uses one persistent connection to ONE room
            CountDownLatch[] ackHolder = new CountDownLatch[1];

            while (true) {
                ChatMessage msg = queue.poll(200, TimeUnit.MILLISECONDS);

                if (msg == null) {
                    if (doneProducing.get() && queue.isEmpty()) break;
                    continue;
                }

                // First message decides this worker's room (persistent)
                if (fixedRoomId == null) {
                    fixedRoomId = msg.roomId;

                    client = buildClient(baseUrl + fixedRoomId, ackHolder);

                    boolean ok = client.connectBlocking(5, TimeUnit.SECONDS);
                    if (!ok) {
                        metrics.failed.incrementAndGet();
                        continue;
                    }
                    metrics.connections.incrementAndGet();
                }

                // force same room
                msg.roomId = fixedRoomId;

                String payload = mapper.writeValueAsString(toServerJson(msg));

                // wait for ack of THIS message
                CountDownLatch ack = new CountDownLatch(1);
                ackHolder[0] = ack;

                boolean sent = sendWithRetry(client, payload);
                if (!sent) {
                    metrics.failed.incrementAndGet();
                    continue;
                }

                boolean acked = ack.await(3, TimeUnit.SECONDS);
                if (!acked) {
                    metrics.failed.incrementAndGet();
                }
            }

        } catch (Exception e) {
            // If something explodes, worker exits. Metrics will show failures.
        } finally {
            try { if (client != null) client.close(); } catch (Exception ignored) {}
        }
    }

    private WebSocketClient buildClient(String url, CountDownLatch[] ackHolder) throws Exception {
        return new WebSocketClient(new URI(url)) {
            @Override public void onOpen(ServerHandshake handshakedata) { }

            @Override public void onMessage(String message) {
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
                    CountDownLatch ack = ackHolder[0];
                    if (ack != null) ack.countDown();
                }
            }

            @Override public void onClose(int code, String reason, boolean remote) { }
            @Override public void onError(Exception ex) { }
        };
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

    private Map<String, Object> toServerJson(ChatMessage m) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("userId", m.userId);
        msg.put("username", m.username);
        msg.put("message", m.message);
        msg.put("timestamp", m.timestamp);
        msg.put("messageType", m.messageType);
        return msg;
    }
}
