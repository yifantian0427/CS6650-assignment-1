package edu.northeastern.cs6650.server.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import edu.northeastern.cs6650.server.dto.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Publishes chat messages to RabbitMQ (topic exchange, routing key room.{roomId}).
 */
@Component
public class QueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(QueuePublisher.class);

    private final ChannelPool channelPool;
    private final QueueConfig config;
    private final CircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueuePublisher(ChannelPool channelPool, QueueConfig config, CircuitBreaker circuitBreaker) {
        this.channelPool = channelPool;
        this.config = config;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Publish a single message and wait for confirm if enabled.
     */
    public boolean publish(QueueMessage msg) {
        if (!circuitBreaker.allowRequest()) return false;
        
        Channel channel = channelPool.borrowChannel();
        if (channel == null) {
            circuitBreaker.recordFailure();
            return false;
        }
        try {
            publishSingle(channel, msg);
            if (config.isPublisherConfirms()) {
                if (!channel.waitForConfirms(5000)) {
                    throw new IOException("Publisher confirm failed");
                }
            }
            circuitBreaker.recordSuccess();
            return true;
        } catch (Exception e) {
            log.warn("Publish failed", e);
            circuitBreaker.recordFailure();
            return false;
        } finally {
            channelPool.returnChannel(channel);
        }
    }

    /**
     * Publish a batch of messages and wait for a single confirm for the whole batch.
     */
    public boolean publishBatch(java.util.List<QueueMessage> messages) {
        if (messages == null || messages.isEmpty()) return true;
        if (!circuitBreaker.allowRequest()) return false;

        Channel channel = channelPool.borrowChannel();
        if (channel == null) {
            circuitBreaker.recordFailure();
            return false;
        }
        try {
            for (QueueMessage msg : messages) {
                publishSingle(channel, msg);
            }
            if (config.isPublisherConfirms()) {
                if (!channel.waitForConfirms(5000)) {
                    throw new IOException("Batch publisher confirm failed");
                }
            }
            circuitBreaker.recordSuccess();
            return true;
        } catch (Exception e) {
            log.warn("Batch publish failed (size={})", messages.size(), e);
            circuitBreaker.recordFailure();
            return false;
        } finally {
            channelPool.returnChannel(channel);
        }
    }

    private void publishSingle(Channel channel, QueueMessage msg) throws IOException {
        String routingKey = config.getRoutingKeyPrefix() + msg.roomId;
        String json = objectMapper.writeValueAsString(msg);
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .contentType("application/json")
                .build();
        channel.basicPublish(
                config.getExchangeName(),
                routingKey,
                props,
                json.getBytes(StandardCharsets.UTF_8)
        );
    }
}
