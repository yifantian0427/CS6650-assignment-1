package edu.northeastern.cs6650.server.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.queue")
public class QueueConfig {

    /** RabbitMQ host */
    private String host = "localhost";
    /** RabbitMQ port */
    private int port = 5672;
    /** Virtual host */
    private String virtualHost = "/";
    /** Username */
    private String username = "guest";
    /** Password */
    private String password = "guest";

    /** Topic exchange name */
    private String exchangeName = "chat.exchange";
    /** Routing key pattern: room.{roomId} */
    private String routingKeyPrefix = "room.";
    /** Number of room queues (room.1 .. room.N) */
    private int numRooms = 20;

    /** Channel pool size */
    private int poolSize = 10;

    /** Queue message TTL (ms). 0 disables TTL. */
    private long messageTtlMs = 0;
    /** Queue max length (messages). 0 disables limit. */
    private int maxLength = 0;
    /** Queue max length (bytes). 0 disables limit. */
    private long maxLengthBytes = 0;

    /** Enable publisher confirms (stronger durability, lower throughput). */
    private boolean publisherConfirms = true;

    /** Circuit breaker: open after this many consecutive failures */
    private int circuitOpenThreshold = 5;
    /** Circuit breaker: wait this many ms before retry */
    private long circuitRetryMs = 10_000;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getVirtualHost() { return virtualHost; }
    public void setVirtualHost(String virtualHost) { this.virtualHost = virtualHost; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getExchangeName() { return exchangeName; }
    public void setExchangeName(String exchangeName) { this.exchangeName = exchangeName; }
    public String getRoutingKeyPrefix() { return routingKeyPrefix; }
    public void setRoutingKeyPrefix(String routingKeyPrefix) { this.routingKeyPrefix = routingKeyPrefix; }
    public int getNumRooms() { return numRooms; }
    public void setNumRooms(int numRooms) { this.numRooms = numRooms; }
    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
    public long getMessageTtlMs() { return messageTtlMs; }
    public void setMessageTtlMs(long messageTtlMs) { this.messageTtlMs = messageTtlMs; }
    public int getMaxLength() { return maxLength; }
    public void setMaxLength(int maxLength) { this.maxLength = maxLength; }
    public long getMaxLengthBytes() { return maxLengthBytes; }
    public void setMaxLengthBytes(long maxLengthBytes) { this.maxLengthBytes = maxLengthBytes; }
    public boolean isPublisherConfirms() { return publisherConfirms; }
    public void setPublisherConfirms(boolean publisherConfirms) { this.publisherConfirms = publisherConfirms; }
    public int getCircuitOpenThreshold() { return circuitOpenThreshold; }
    public void setCircuitOpenThreshold(int circuitOpenThreshold) { this.circuitOpenThreshold = circuitOpenThreshold; }
    public long getCircuitRetryMs() { return circuitRetryMs; }
    public void setCircuitRetryMs(long circuitRetryMs) { this.circuitRetryMs = circuitRetryMs; }
}
