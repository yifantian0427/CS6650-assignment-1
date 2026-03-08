package edu.northeastern.cs6650.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
public class ConsumerProperties {

    private Rabbitmq rabbitmq = new Rabbitmq();
    private Consumer consumer = new Consumer();
    private Servers servers = new Servers();

    public static class Rabbitmq {
        private String host = "localhost";
        private int port = 5672;
        private String virtualHost = "/";
        private String username = "guest";
        private String password = "guest";
        private String exchangeName = "chat.exchange";
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
        public String getExchangeName() { return exchangeName; }
        public void setExchangeName(String exchangeName) { this.exchangeName = exchangeName; }
        public int getNumRooms() { return numRooms; }
        public void setNumRooms(int numRooms) { this.numRooms = numRooms; }
    }

    public static class Consumer {
        private int threads = 20;
        private int prefetchCount = 10;

        public int getThreads() { return threads; }
        public void setThreads(int threads) { this.threads = threads; }
        public int getPrefetchCount() { return prefetchCount; }
        public void setPrefetchCount(int prefetchCount) { this.prefetchCount = prefetchCount; }
    }

    public static class Servers {
        /** Comma-separated base URLs, e.g. http://host1:8080,http://host2:8080 */
        private String urls = "http://localhost:8080";

        public String getUrls() { return urls; }
        public void setUrls(String urls) { this.urls = urls; }

        public List<String> getUrlList() {
            if (urls == null || urls.isBlank()) return Collections.emptyList();
            return List.of(urls.split("\\s*,\\s*"));
        }
    }

    public Rabbitmq getRabbitmq() { return rabbitmq; }
    public void setRabbitmq(Rabbitmq rabbitmq) { this.rabbitmq = rabbitmq; }
    public Consumer getConsumer() { return consumer; }
    public void setConsumer(Consumer consumer) { this.consumer = consumer; }
    public Servers getServers() { return servers; }
    public void setServers(Servers servers) { this.servers = servers; }
}
