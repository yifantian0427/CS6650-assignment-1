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
    private final int roomId; // FIXED room for this worker

    private final BlockingQueue<ChatMessage> roomQueue;
    private final AtomicBoolean doneProducing;
    private final LatencyTracker latencyTracker;

    private final int batchSize;
    private final int delayMs;

    public static final AtomicInteger totalSuccess = new AtomicInteger(0);
    public static final AtomicInteger totalRetries = new AtomicInteger(0);

    private final ObjectMapper mapper = new ObjectMapper();
    private final long ackTimeoutMs = 3000;

    /** Only log first few server errors to stderr to avoid flood */
    private static final AtomicInteger serverErrorLogCount = new AtomicInteger(0);
    private static final int MAX_SERVER_ERROR_LOGS = 3;

    public SenderWorker(String baseUrl,
            int roomId,
            BlockingQueue<ChatMessage> roomQueue,
            AtomicBoolean doneProducing,
            LatencyTracker latencyTracker,
            int batchSize,
            int delayMs) {
        this.baseUrl = baseUrl;
        this.roomId = roomId;
        this.roomQueue = roomQueue;
        this.doneProducing = doneProducing;
        this.latencyTracker = latencyTracker;
        this.batchSize = batchSize;
        this.delayMs = delayMs;
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
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                }

                @Override
                public void onMessage(String message) {
                    inbox.offer(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                }

                @Override
                public void onError(Exception ex) {
                }
            };

            client.connectBlocking();

            while (true) {
                java.util.List<ChatMessage> batch = new java.util.ArrayList<>();
                roomQueue.drainTo(batch, batchSize);
                
                if (batch.isEmpty()) {
                    if (doneProducing.get()) break;
                    ChatMessage single = roomQueue.poll(200, TimeUnit.MILLISECONDS);
                    if (single != null) batch.add(single);
                    else continue;
                }

                String payload = mapper.writeValueAsString(batch);
                boolean delivered = false;
                int retries = 0;
                int maxRetries = 10;

                while (retries <= maxRetries && !delivered) {
                    long startMs = System.currentTimeMillis();
                    client.send(payload);

                    String ack = inbox.poll(ackTimeoutMs, TimeUnit.MILLISECONDS);
                    long endMs = System.currentTimeMillis();
                    long latency = endMs - startMs;

                    if (ack == null) {
                        retries++;
                        totalRetries.incrementAndGet();
                        for (ChatMessage msg : batch) {
                            latencyTracker.recordTimeout(msg.messageType, roomId);
                        }
                        if (retries <= maxRetries) {
                            Thread.sleep(1000);
                            continue;
                        }
                        break;
                    }

                    if (isSuccessAck(ack)) {
                        for (ChatMessage msg : batch) {
                            latencyTracker.recordSuccess(msg.messageType, latency, roomId);
                        }
                        totalSuccess.addAndGet(batch.size());
                        delivered = true;
                    } else {
                        String errorMsg = getErrorMsg(ack);
                        if (errorMsg != null && errorMsg.contains("Queue unavailable")) {
                            retries++;
                            totalRetries.incrementAndGet();
                            if (retries <= maxRetries) {
                                Thread.sleep(1000);
                                continue;
                            }
                        }
                        logServerError(ack, roomId);
                        for (ChatMessage msg : batch) {
                            latencyTracker.recordServerError(msg.messageType, latency, roomId);
                        }
                        break;
                    }
                }
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            }
        } catch (Exception e) {
            latencyTracker.recordClientError("UNKNOWN", roomId);
        } finally {
            try {
                if (client != null) client.close();
            } catch (Exception ignored) {}
        }
    }


    private boolean isSuccessAck(String ackJson) {
        try {
            JsonNode root = mapper.readTree(ackJson);
            String status = root.path("status").asText("");
            return "SUCCESS".equalsIgnoreCase(status) || "BATCH_SUCCESS".equalsIgnoreCase(status);
        } catch (Exception e) {
            return false;
        }
    }

    private String getErrorMsg(String ackJson) {
        try {
            JsonNode root = mapper.readTree(ackJson);
            return root.path("errorMessage").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private void logServerError(String ackJson, int roomId) {
        if (serverErrorLogCount.incrementAndGet() > MAX_SERVER_ERROR_LOGS)
            return;
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
