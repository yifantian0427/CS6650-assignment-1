package edu.northeastern.cs6650.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import edu.northeastern.cs6650.consumer.config.ConsumerProperties;
import edu.northeastern.cs6650.consumer.dto.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Pulls messages from RabbitMQ room queues and broadcasts to server-v2 instances.
 */
@Component
public class QueueConsumerRunner {

    private static final Logger log = LoggerFactory.getLogger(QueueConsumerRunner.class);

    private final ConsumerProperties properties;
    private final BroadcastClient broadcastClient;
    private final Deduplicator deduplicator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Connection connection;
    private ExecutorService executor;

    public QueueConsumerRunner(ConsumerProperties properties, BroadcastClient broadcastClient, Deduplicator deduplicator) {
        this.properties = properties;
        this.broadcastClient = broadcastClient;
        this.deduplicator = deduplicator;
    }

    /** Start consumer threads (one per room queue). */
    public void start() {
        ConsumerProperties.Rabbitmq rmq = properties.getRabbitmq();
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rmq.getHost());
            factory.setPort(rmq.getPort());
            factory.setVirtualHost(rmq.getVirtualHost());
            factory.setUsername(rmq.getUsername());
            factory.setPassword(rmq.getPassword());
            connection = factory.newConnection();

            int numRooms = rmq.getNumRooms();
            int poolSize = Math.max(1, properties.getConsumer().getThreads());
            executor = Executors.newFixedThreadPool(poolSize);

            for (int i = 1; i <= numRooms; i++) {
                String queueName = "room." + i;
                executor.submit(() -> runConsumer(queueName));
            }
            log.info("Started consumers for room.1 .. room.{} (thread pool size {})", numRooms, poolSize);
        } catch (IOException | TimeoutException e) {
            log.error("Failed to start consumers", e);
            throw new RuntimeException(e);
        }
    }

    private void runConsumer(String queueName) {
        try {
            Channel channel = connection.createChannel();
            channel.basicQos(properties.getConsumer().getPrefetchCount());

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    QueueMessage msg = objectMapper.readValue(body, QueueMessage.class);
                    String roomId = msg.roomId != null ? msg.roomId : queueName.replace("room.", "");
                    if (!deduplicator.shouldProcess(roomId, msg.messageId)) {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        return;
                    }

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

                    String payload = objectMapper.writeValueAsString(broadcast);
                    boolean allOk = broadcastClient.broadcastToRoom(roomId, payload);
                    if (!allOk) {
                        throw new IOException("broadcast failed to one or more servers");
                    }

                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    log.warn("Error processing message from {}: {}", queueName, e.getMessage());
                    try {
                        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                    } catch (IOException ignored) {}
                }
            };

            channel.basicConsume(queueName, false, deliverCallback, tag -> {});
        } catch (IOException e) {
            log.error("Consumer for {} failed: {}", queueName, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdown();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) {}
        }
    }
}
