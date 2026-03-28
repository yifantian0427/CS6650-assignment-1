package edu.northeastern.cs6650.consumerv3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
public class ConsumerV3Properties {
    private Rabbitmq rabbitmq = new Rabbitmq();
    private Consumer consumer = new Consumer();
    private Servers servers = new Servers();
    private Persistence persistence = new Persistence();
    private Retry retry = new Retry();

    public static class Rabbitmq {
        private String host = "localhost";
        private int port = 5672;
        private String virtualHost = "/";
        private String username = "guest";
        private String password = "guest";
        private int numRooms = 20;
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
        public int getNumRooms() { return numRooms; }
        public void setNumRooms(int numRooms) { this.numRooms = numRooms; }
    }

    public static class Consumer {
        private int threads = 20;
        private int prefetchCount = 100;
        public int getThreads() { return threads; }
        public void setThreads(int threads) { this.threads = threads; }
        public int getPrefetchCount() { return prefetchCount; }
        public void setPrefetchCount(int prefetchCount) { this.prefetchCount = prefetchCount; }
    }

    public static class Servers {
        private String urls = "http://localhost:8080";
        public String getUrls() { return urls; }
        public void setUrls(String urls) { this.urls = urls; }
        public List<String> getUrlList() {
            if (urls == null || urls.isBlank()) return Collections.emptyList();
            return List.of(urls.split("\\s*,\\s*"));
        }
    }

    public static class Persistence {
        private int dbWriterThreads = 2;
        private int batchSize = 500;
        private long flushIntervalMs = 500;
        private int queueCapacity = 50_000;
        public int getDbWriterThreads() { return dbWriterThreads; }
        public void setDbWriterThreads(int dbWriterThreads) { this.dbWriterThreads = dbWriterThreads; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public long getFlushIntervalMs() { return flushIntervalMs; }
        public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    }

    public static class Retry {
        private int maxAttempts = 5;
        private long backoffBaseMs = 200;
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getBackoffBaseMs() { return backoffBaseMs; }
        public void setBackoffBaseMs(long backoffBaseMs) { this.backoffBaseMs = backoffBaseMs; }
    }

    public Rabbitmq getRabbitmq() { return rabbitmq; }
    public void setRabbitmq(Rabbitmq rabbitmq) { this.rabbitmq = rabbitmq; }
    public Consumer getConsumer() { return consumer; }
    public void setConsumer(Consumer consumer) { this.consumer = consumer; }
    public Servers getServers() { return servers; }
    public void setServers(Servers servers) { this.servers = servers; }
    public Persistence getPersistence() { return persistence; }
    public void setPersistence(Persistence persistence) { this.persistence = persistence; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
}
