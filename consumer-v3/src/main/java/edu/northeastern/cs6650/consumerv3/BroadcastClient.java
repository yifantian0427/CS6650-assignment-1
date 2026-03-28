package edu.northeastern.cs6650.consumerv3;

import edu.northeastern.cs6650.consumerv3.config.ConsumerV3Properties;
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

@Component
public class BroadcastClient {
    private static final Logger log = LoggerFactory.getLogger(BroadcastClient.class);
    private final ConsumerV3Properties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public BroadcastClient(ConsumerV3Properties properties) {
        this.properties = properties;
    }

    public boolean broadcastToRoom(String roomId, String payload) {
        List<String> urls = properties.getServers().getUrlList();
        if (urls.isEmpty()) return false;

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
                log.warn("Broadcast failed to {}: {}", url, e.getMessage());
                allOk = false;
            }
        }
        return allOk;
    }
}
