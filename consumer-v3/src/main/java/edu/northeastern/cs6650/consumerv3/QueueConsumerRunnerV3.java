package edu.northeastern.cs6650.consumerv3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import edu.northeastern.cs6650.consumerv3.config.ConsumerV3Properties;
import edu.northeastern.cs6650.consumerv3.dto.QueueMessage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class QueueConsumerRunnerV3 {
    private static final Logger log = LoggerFactory.getLogger(QueueConsumerRunnerV3.class);

    private final ConsumerV3Properties properties;
    private final BroadcastClient broadcastClient;
    private final Deduplicator deduplicator;
    private final PersistenceService persistenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Connection connection;
    private ExecutorService executor;
    private final AtomicLong consumed = new AtomicLong(0);
    private final AtomicLong broadcastErrors = new AtomicLong(0);
    private final AtomicLong enqueueErrors = new AtomicLong(0);

    public QueueConsumerRunnerV3(
            ConsumerV3Properties properties,
            BroadcastClient broadcastClient,
            Deduplicator deduplicator,
            PersistenceService persistenceService) {
        this.properties = properties;
        this.broadcastClient = broadcastClient;
        this.deduplicator = deduplicator;
        this.persistenceService = persistenceService;
    }

    public void start() {
        ConsumerV3Properties.Rabbitmq rmq = properties.getRabbitmq();
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rmq.getHost());
            factory.setPort(rmq.getPort());
            factory.setVirtualHost(rmq.getVirtualHost());
            factory.setUsername(rmq.getUsername());
            factory.setPassword(rmq.getPassword());
            connection = factory.newConnection();

            int poolSize = Math.max(1, properties.getConsumer().getThreads());
            executor = Executors.newFixedThreadPool(poolSize);
            for (int i = 1; i <= rmq.getNumRooms(); i++) {
                String queueName = "room." + i;
                executor.submit(() -> runConsumer(queueName));
            }
            log.info("consumer-v3 started for room.1..room.{}", rmq.getNumRooms());
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Failed to start consumer-v3", e);
        }
    }

    private void runConsumer(String queueName) {
        try {
            Channel channel = connection.createChannel();
            channel.basicQos(properties.getConsumer().getPrefetchCount());

            DeliverCallback callback = (tag, delivery) -> {
                try {
                    String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    QueueMessage msg = objectMapper.readValue(body, QueueMessage.class);
                    String roomId = (msg.roomId == null || msg.roomId.isBlank()) ? queueName.replace("room.", "") : msg.roomId;

                    if (!deduplicator.shouldProcess(roomId, msg.messageId)) {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        return;
                    }

                    consumed.incrementAndGet();
                    String payload = buildBroadcastPayload(roomId, msg);
                    boolean ok = broadcastClient.broadcastToRoom(roomId, payload);
                    if (!ok) {
                        broadcastErrors.incrementAndGet();
                    }

                    if (!persistenceService.enqueue(msg)) {
                        enqueueErrors.incrementAndGet();
                        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                        return;
                    }

                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    log.warn("consumer-v3 process error on {}: {}", queueName, e.getMessage());
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                }
            };

            channel.basicConsume(queueName, false, callback, consumerTag -> { });
        } catch (IOException e) {
            log.error("consumer-v3 thread failed on {}: {}", queueName, e.getMessage());
        }
    }

    private String buildBroadcastPayload(String roomId, QueueMessage msg) throws Exception {
        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("status", "SUCCESS");
        broadcast.put("serverTimestamp", java.time.Instant.now().toString());
        broadcast.put("roomId", roomId);
        Map<String, Object> original = new HashMap<>();
        original.put("userId", msg.userId);
        original.put("username", msg.username);
        original.put("message", msg.message);
        original.put("timestamp", msg.timestamp);
        original.put("messageType", msg.messageType);
        broadcast.put("originalMessage", original);
        return objectMapper.writeValueAsString(broadcast);
    }

    public long getConsumed() { return consumed.get(); }
    public long getBroadcastErrors() { return broadcastErrors.get(); }
    public long getEnqueueErrors() { return enqueueErrors.get(); }

    @PreDestroy
    public void stop() {
        if (executor != null) executor.shutdownNow();
        if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
        }
    }
}
