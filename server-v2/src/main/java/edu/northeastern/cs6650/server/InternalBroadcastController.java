package edu.northeastern.cs6650.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal endpoint for the consumer to request broadcast to a room.
 * Called by the consumer after pulling a message from the queue.
 */
@RestController
@RequestMapping("/internal")
public class InternalBroadcastController {

    private final RoomSessionRegistry roomSessions;

    public InternalBroadcastController(RoomSessionRegistry roomSessions) {
        this.roomSessions = roomSessions;
    }

    /**
     * Supports both single map and list of maps for batching.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/broadcast")
    public ResponseEntity<Void> broadcast(@RequestBody Object body) {
        if (body instanceof java.util.List) {
            java.util.List<Map<String, String>> batch = (java.util.List<Map<String, String>>) body;
            for (Map<String, String> item : batch) {
                processSingle(item);
            }
        } else if (body instanceof Map) {
            processSingle((Map<String, String>) body);
        } else {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    private void processSingle(Map<String, String> item) {
        String roomId = item.get("roomId");
        String payload = item.get("payload");
        if (roomId != null && payload != null) {
            roomSessions.broadcast(roomId, payload);
        }
    }
}
