package edu.northeastern.cs6650.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort duplicate filtering for at-least-once delivery.
 * Tracks last N messageIds per room in-memory.
 */
@Component
public class Deduplicator {

    private final int perRoomCapacity;
    private final ConcurrentHashMap<String, RoomWindow> windows = new ConcurrentHashMap<>();

    public Deduplicator(@Value("${app.dedupe.per-room-capacity:10000}") int perRoomCapacity) {
        this.perRoomCapacity = Math.max(1000, perRoomCapacity);
    }

    /**
     * @return true if this messageId is new for the room and should be processed.
     */
    public boolean shouldProcess(String roomId, String messageId) {
        if (roomId == null || roomId.isBlank() || messageId == null || messageId.isBlank()) {
            return true;
        }
        RoomWindow w = windows.computeIfAbsent(roomId, k -> new RoomWindow(perRoomCapacity));
        return w.addIfAbsent(messageId);
    }

    private static final class RoomWindow {
        private final int cap;
        private final Deque<String> order = new ArrayDeque<>();
        private final Set<String> set = new HashSet<>();

        private RoomWindow(int cap) {
            this.cap = cap;
        }

        private synchronized boolean addIfAbsent(String id) {
            if (set.contains(id)) return false;
            set.add(id);
            order.addLast(id);
            while (order.size() > cap) {
                String evict = order.removeFirst();
                set.remove(evict);
            }
            return true;
        }
    }
}

