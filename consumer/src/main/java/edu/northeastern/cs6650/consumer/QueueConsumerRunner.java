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
            channel.basicQos(Math.max(100, properties.getConsumer().getPrefetchCount()));

            final java.util.concurrent.BlockingQueue<Delivery> buffer = new java.util.concurrent.LinkedBlockingQueue<>();

            // Batch processing thread
            Thread batcher = new Thread(() -> {
                while (true) {
                    java.util.List<Delivery> currentBatch = new java.util.ArrayList<>();
                    try {
                        Delivery first = buffer.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (first != null) {
                            currentBatch.add(first);
                            buffer.drainTo(currentBatch, 49); // Max batch size 50
                        }
                    } catch (InterruptedException e) {
                        break;
                    }

                    if (currentBatch.isEmpty()) continue;

                    java.util.List<Map<String, String>> broadcastBatch = new java.util.ArrayList<>();
                    for (Delivery delivery : currentBatch) {
                        try {
                            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                            QueueMessage msg = objectMapper.readValue(body, QueueMessage.class);
                            String roomId = msg.roomId != null ? msg.roomId : queueName.replace("room.", "");
                            
                            if (deduplicator.shouldProcess(roomId, msg.messageId)) {
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

                                Map<String, String> item = new HashMap<>();
                                item.put("roomId", roomId);
                                item.put("payload", objectMapper.writeValueAsString(broadcast));
                                broadcastBatch.add(item);
                            }
                        } catch (Exception e) {
                            log.warn("Error parsing message for batch: {}", e.getMessage());
                        }
                    }

                    if (!broadcastBatch.isEmpty()) {
                        boolean allOk = broadcastClient.broadcastBatch(broadcastBatch);
                        if (allOk) {
                            // Ack all in batch
                            for (Delivery delivery : currentBatch) {
                                try {
                                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                                } catch (IOException e) {
                                    log.error("Failed to ack message in {}: {}", queueName, e.getMessage());
                                }
                            }
                        } else {
                            log.warn("Batch broadcast failed for {} items in {}. Requeueing.", broadcastBatch.size(), queueName);
                            // Nack all in batch and requeue
                            for (Delivery delivery : currentBatch) {
                                try {
                                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                                } catch (IOException e) {
                                    log.error("Failed to nack message in {}: {}", queueName, e.getMessage());
                                }
                            }
                        }
                    } else {
                        // All messages were either empty (parse error) or duplicates. 
                        // Still need to ack them so they don't stay in MQ.
                        for (Delivery delivery : currentBatch) {
                            try {
                                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                            } catch (IOException e) {
                                log.error("Failed to ack message in {}: {}", queueName, e.getMessage());
                            }
                        }
                    }
                }
            });
            batcher.setDaemon(true);
            batcher.start();

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                buffer.offer(delivery);
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
