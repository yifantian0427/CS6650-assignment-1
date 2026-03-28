package edu.northeastern.cs6650.server;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricsQueryService {
    private final JdbcTemplate jdbcTemplate;

    public MetricsQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> fetchMetrics(String roomId, String userId, String startTime, String endTime, int topN) {
        Timestamp startTs = parseTs(startTime, Instant.now().minusSeconds(3600));
        Timestamp endTs = parseTs(endTime, Instant.now());

        Map<String, Object> out = new HashMap<>();
        out.put("queryWindow", Map.of("startTime", startTs.toInstant().toString(), "endTime", endTs.toInstant().toString()));

        List<Map<String, Object>> roomMessages = jdbcTemplate.queryForList(
                "SELECT message_id, room_id, user_id, username, message, message_type, message_ts " +
                        "FROM chat_messages WHERE room_id = ? AND message_ts BETWEEN ? AND ? " +
                        "ORDER BY message_ts ASC LIMIT 1000",
                roomId, startTs, endTs);
        out.put("messagesForRoomInRange", roomMessages);

        List<Map<String, Object>> userHistory = jdbcTemplate.queryForList(
                "SELECT message_id, room_id, user_id, username, message, message_type, message_ts " +
                        "FROM chat_messages WHERE user_id = ? AND message_ts BETWEEN ? AND ? " +
                        "ORDER BY message_ts DESC LIMIT 2000",
                userId, startTs, endTs);
        out.put("userMessageHistory", userHistory);

        Integer activeUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM chat_messages WHERE message_ts BETWEEN ? AND ?",
                Integer.class, startTs, endTs);
        out.put("activeUsersInWindow", activeUsers == null ? 0 : activeUsers);

        List<Map<String, Object>> roomsByUser = jdbcTemplate.queryForList(
                "SELECT room_id, MAX(message_ts) AS last_activity " +
                        "FROM chat_messages WHERE user_id = ? GROUP BY room_id ORDER BY last_activity DESC",
                userId);
        out.put("roomsUserParticipatedIn", roomsByUser);

        List<Map<String, Object>> perSecond = jdbcTemplate.queryForList(
                "SELECT date_trunc('second', message_ts) AS second_bucket, COUNT(*) AS count " +
                        "FROM chat_messages WHERE message_ts BETWEEN ? AND ? " +
                        "GROUP BY second_bucket ORDER BY second_bucket ASC",
                startTs, endTs);
        out.put("messagesPerSecond", perSecond);

        List<Map<String, Object>> topUsers = jdbcTemplate.queryForList(
                "SELECT user_id, COUNT(*) AS message_count FROM chat_messages " +
                        "WHERE message_ts BETWEEN ? AND ? GROUP BY user_id ORDER BY message_count DESC LIMIT ?",
                startTs, endTs, topN);
        out.put("topUsers", topUsers);

        List<Map<String, Object>> topRooms = jdbcTemplate.queryForList(
                "SELECT room_id, COUNT(*) AS message_count FROM chat_messages " +
                        "WHERE message_ts BETWEEN ? AND ? GROUP BY room_id ORDER BY message_count DESC LIMIT ?",
                startTs, endTs, topN);
        out.put("topRooms", topRooms);

        List<Map<String, Object>> participation = jdbcTemplate.queryForList(
                "SELECT user_id, COUNT(DISTINCT room_id) AS rooms_joined, COUNT(*) AS total_messages " +
                        "FROM chat_messages WHERE message_ts BETWEEN ? AND ? " +
                        "GROUP BY user_id ORDER BY total_messages DESC LIMIT ?",
                startTs, endTs, topN);
        out.put("userParticipationPatterns", participation);
        return out;
    }

    private Timestamp parseTs(String value, Instant fallback) {
        try {
            return Timestamp.from(Instant.parse(value));
        } catch (Exception e) {
            return Timestamp.from(fallback);
        }
    }
}
