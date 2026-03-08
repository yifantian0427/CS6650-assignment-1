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
    public boolean broadcastToRoom(String roomId, String payload) {
        List<String> urls = properties.getServers().getUrlList();
        if (urls.isEmpty()) {
            log.warn("No server URLs configured for broadcast");
            return false;
        }
        Map<String, String> body = new HashMap<>();
        body.put("roomId", roomId);
        body.put("payload", payload);

        boolean allOk = true;
        for (String baseUrl : urls) {
            String url = baseUrl.replaceAll("/$", "") + "/internal/broadcast";
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Void.class);
            } catch (Exception e) {
                log.debug("Broadcast to {} failed: {}", url, e.getMessage());
                allOk = false;
            }
        }
        return allOk;
    }
}
