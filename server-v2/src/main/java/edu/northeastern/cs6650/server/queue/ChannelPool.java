package edu.northeastern.cs6650.server.queue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe pool of RabbitMQ channels. Borrow/return for publishing.
 */
@Component
public class ChannelPool {

    private static final Logger log = LoggerFactory.getLogger(ChannelPool.class);

    private final QueueConfig config;
    private final CircuitBreaker circuitBreaker;

    private volatile Connection connection;
    private final BlockingQueue<Channel> pool;

    public ChannelPool(QueueConfig config, CircuitBreaker circuitBreaker) {
        this.config = config;
        this.circuitBreaker = circuitBreaker;
        this.pool = new ArrayBlockingQueue<>(config.getPoolSize());
    }

    /** Initialize connection and pre-create channels. Call after properties are set. */
    public void init() {
        if (connection != null) {
            return;
        }
        synchronized (this) {
            if (connection != null) return;
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(config.getHost());
                factory.setPort(config.getPort());
                factory.setVirtualHost(config.getVirtualHost());
                factory.setUsername(config.getUsername());
                factory.setPassword(config.getPassword());
                connection = factory.newConnection();
                int size = config.getPoolSize();
                for (int i = 0; i < size; i++) {
                    Channel ch = connection.createChannel();
                    ch.exchangeDeclare(config.getExchangeName(), "topic", true);
                    if (config.isPublisherConfirms()) {
                        ch.confirmSelect();
                    }
                    for (int r = 1; r <= config.getNumRooms(); r++) {
                        String queueName = config.getRoutingKeyPrefix() + r;
                        ch.queueDeclare(queueName, true, false, false, queueArgs());
                        ch.queueBind(queueName, config.getExchangeName(), queueName);
                    }
                    pool.offer(ch);
                }
                log.info("Channel pool initialized with {} channels", size);
            } catch (Exception e) {
                log.error("Failed to initialize channel pool", e);
                circuitBreaker.recordFailure();
                // Graceful: allow server to start even if queue is down.
                // Publisher will fail until we can reconnect.
                connection = null;
            }
        }
    }

    /** Borrow a channel from the pool. Returns null if pool not initialized or timeout. */
    public Channel borrowChannel() {
        if (connection == null || !connection.isOpen()) {
            // Try to reconnect if circuit allows it
            if (circuitBreaker.allowRequest()) {
                init();
            }
            return null;
        }
        try {
            return pool.poll(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Return a channel to the pool. If channel is broken, does not re-insert. */
    public void returnChannel(Channel channel) {
        if (channel == null) return;
        try {
            if (channel.isOpen()) {
                pool.offer(channel);
            } else {
                try { channel.close(); } catch (Exception ignored) {}
                tryCreateReplacement();
            }
        } catch (Exception e) {
            try { if (channel != null) channel.close(); } catch (Exception ignored) {}
            tryCreateReplacement();
        }
    }

    private void tryCreateReplacement() {
        try {
            if (connection != null && connection.isOpen()) {
                Channel ch = connection.createChannel();
                ch.exchangeDeclare(config.getExchangeName(), "topic", true);
                if (config.isPublisherConfirms()) {
                    ch.confirmSelect();
                }
                for (int r = 1; r <= config.getNumRooms(); r++) {
                    String queueName = config.getRoutingKeyPrefix() + r;
                    ch.queueDeclare(queueName, true, false, false, queueArgs());
                    ch.queueBind(queueName, config.getExchangeName(), queueName);
                }
                pool.offer(ch);
            }
        } catch (Exception e) {
            log.warn("Could not create replacement channel", e);
        }
    }

    private Map<String, Object> queueArgs() {
        Map<String, Object> args = new HashMap<>();
        if (config.getMessageTtlMs() > 0) {
            args.put("x-message-ttl", config.getMessageTtlMs());
        }
        if (config.getMaxLength() > 0) {
            args.put("x-max-length", config.getMaxLength());
        }
        if (config.getMaxLengthBytes() > 0) {
            args.put("x-max-length-bytes", config.getMaxLengthBytes());
        }
        return args.isEmpty() ? null : args;
    }

    public boolean isAvailable() {
        return connection != null && connection.isOpen();
    }

    @PreDestroy
    public void close() {
        Channel ch;
        while ((ch = pool.poll()) != null) {
            try { ch.close(); } catch (Exception ignored) {}
        }
        if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
        }
        log.info("Channel pool closed");
    }
}
