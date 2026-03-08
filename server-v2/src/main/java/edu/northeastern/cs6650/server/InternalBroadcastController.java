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
     * Body: { "roomId": "5", "payload": "<JSON string to send to all sessions in room>" }
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Void> broadcast(@RequestBody Map<String, String> body) {
        String roomId = body.get("roomId");
        String payload = body.get("payload");
        if (roomId == null || payload == null) {
            return ResponseEntity.badRequest().build();
        }
        roomSessions.broadcast(roomId, payload);
        return ResponseEntity.ok().build();
    }
}
