package edu.northeastern.cs6650.consumer;

import edu.northeastern.cs6650.consumer.config.ConsumerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends broadcast request to each server-v2 instance (POST /internal/broadcast).
 */
@Component
public class BroadcastClient {

    private static final Logger log = LoggerFactory.getLogger(BroadcastClient.class);

    private final ConsumerProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public BroadcastClient(ConsumerProperties properties) {
        this.properties = properties;
    }

    /**
     * Broadcast payload to all sessions in the room by POSTing to each server-v2 URL.
     */
    /**
     * @return true if all configured servers accepted the broadcast
     */
    public boolean broadcastBatch(List<Map<String, String>> batch) {
        List<String> urls = properties.getServers().getUrlList();
        if (urls.isEmpty()) {
            log.warn("No server URLs configured for broadcast");
            return false;
        }

        // Use parallelStream to hit all servers concurrently
        // This ensures broadcast time doesn't scale linearly with server count
        return urls.parallelStream().allMatch(baseUrl -> {
            String url = baseUrl.replaceAll("/$", "") + "/internal/broadcast";
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                restTemplate.postForEntity(url, new HttpEntity<>(batch, headers), Void.class);
                return true;
            } catch (Exception e) {
                log.warn("Batch broadcast to {} failed: {}", url, e.getMessage());
                return false;
            }
        });
    }

    public boolean broadcastToRoom(String roomId, String payload) {
        Map<String, String> body = new HashMap<>();
        body.put("roomId", roomId);
        body.put("payload", payload);

        List<String> urls = properties.getServers().getUrlList();
        if (urls.isEmpty()) {
            log.warn("No server URLs configured for broadcast");
            return false;
        }

        return urls.parallelStream().allMatch(baseUrl -> {
            String url = baseUrl.replaceAll("/$", "") + "/internal/broadcast";
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Void.class);
                return true;
            } catch (Exception e) {
                log.warn("Broadcast to {} failed: {}", url, e.getMessage());
                return false;
            }
        });
    }
}
