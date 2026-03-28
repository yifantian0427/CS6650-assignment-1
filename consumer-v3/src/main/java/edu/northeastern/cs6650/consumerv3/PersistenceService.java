package edu.northeastern.cs6650.consumerv3;

import edu.northeastern.cs6650.consumerv3.config.ConsumerV3Properties;
import edu.northeastern.cs6650.consumerv3.dto.QueueMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PersistenceService {
    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);

    private final ConsumerV3Properties properties;
    private final JdbcTemplate jdbcTemplate;
    private final DbCircuitBreaker circuitBreaker;

    private final BlockingQueue<PersistRequest> queue;
    private ExecutorService writerPool;
    private final AtomicLong persisted = new AtomicLong(0);
    private final AtomicLong deadLettered = new AtomicLong(0);

    public PersistenceService(ConsumerV3Properties properties, JdbcTemplate jdbcTemplate, DbCircuitBreaker circuitBreaker) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.circuitBreaker = circuitBreaker;
        this.queue = new ArrayBlockingQueue<>(properties.getPersistence().getQueueCapacity());
    }

    @PostConstruct
    public void start() {
        int workers = Math.max(1, properties.getPersistence().getDbWriterThreads());
        writerPool = Executors.newFixedThreadPool(workers);
        for (int i = 0; i < workers; i++) {
            writerPool.submit(this::runWriterLoop);
        }
        log.info("Persistence writers started: {}", workers);
    }

    public boolean enqueue(QueueMessage msg) {
        return queue.offer(new PersistRequest(msg, 1));
    }

    public long getPersistedCount() { return persisted.get(); }
    public long getDeadLetterCount() { return deadLettered.get(); }
    public int getQueueSize() { return queue.size(); }

    private void runWriterLoop() {
        List<PersistRequest> batch = new ArrayList<>();
        int batchSize = Math.max(1, properties.getPersistence().getBatchSize());
        long flushIntervalMs = Math.max(50, properties.getPersistence().getFlushIntervalMs());

        while (!Thread.currentThread().isInterrupted()) {
            try {
                PersistRequest first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) batch.add(first);
                queue.drainTo(batch, batchSize - batch.size());
                if (!batch.isEmpty()) {
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("Writer loop error: {}", e.getMessage());
            }
        }
    }

    private void flushBatch(List<PersistRequest> batch) {
        if (!circuitBreaker.allowRequest()) {
            retryOrDeadLetter(batch, "db circuit open");
            return;
        }
        try {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO chat_messages " +
                            "(message_id, room_id, user_id, username, message, message_type, message_ts, server_id, client_ip, ingested_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now()) " +
                            "ON CONFLICT (message_id) DO NOTHING",
                    batch,
                    batch.size(),
                    (ps, req) -> {
                        QueueMessage m = req.getMessage();
                        ps.setString(1, m.messageId);
                        ps.setString(2, m.roomId);
                        ps.setString(3, m.userId);
                        ps.setString(4, m.username);
                        ps.setString(5, m.message);
                        ps.setString(6, m.messageType);
                        ps.setTimestamp(7, parseTs(m.timestamp));
                        ps.setString(8, m.serverId);
                        ps.setString(9, m.clientIp);
                    });

            jdbcTemplate.batchUpdate(
                    "INSERT INTO room_user_activity (room_id, user_id, last_activity_ts) VALUES (?, ?, ?) " +
                            "ON CONFLICT (room_id, user_id) DO UPDATE SET last_activity_ts = GREATEST(room_user_activity.last_activity_ts, EXCLUDED.last_activity_ts)",
                    batch,
                    batch.size(),
                    (ps, req) -> {
                        QueueMessage m = req.getMessage();
                        ps.setString(1, m.roomId);
                        ps.setString(2, m.userId);
                        ps.setTimestamp(3, parseTs(m.timestamp));
                    });

            circuitBreaker.recordSuccess();
            persisted.addAndGet(batch.size());
        } catch (Exception e) {
            log.warn("DB batch write failed: {}", e.getMessage());
            circuitBreaker.recordFailure();
            retryOrDeadLetter(batch, e.getMessage());
        }
    }

    private void retryOrDeadLetter(List<PersistRequest> failedBatch, String reason) {
        int maxAttempts = Math.max(1, properties.getRetry().getMaxAttempts());
        long base = Math.max(10, properties.getRetry().getBackoffBaseMs());
        for (PersistRequest req : failedBatch) {
            if (req.getAttempt() >= maxAttempts) {
                writeDeadLetter(req.getMessage(), reason);
                deadLettered.incrementAndGet();
                continue;
            }
            long backoffMs = base * (1L << Math.min(10, req.getAttempt() - 1));
            try {
                Thread.sleep(Math.min(5000, backoffMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            queue.offer(new PersistRequest(req.getMessage(), req.getAttempt() + 1));
        }
    }

    private void writeDeadLetter(QueueMessage m, String reason) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO failed_messages " +
                            "(message_id, room_id, user_id, payload, failure_reason, failed_at) " +
                            "VALUES (?, ?, ?, to_jsonb(?::json), ?, now()) " +
                            "ON CONFLICT (message_id) DO UPDATE SET failure_reason = EXCLUDED.failure_reason, failed_at = now()",
                    m.messageId,
                    m.roomId,
                    m.userId,
                    "{\"messageId\":\"" + safe(m.messageId) + "\",\"roomId\":\"" + safe(m.roomId) + "\",\"userId\":\"" + safe(m.userId) + "\"}",
                    reason
            );
        } catch (Exception ignored) {
        }
    }

    private Timestamp parseTs(String ts) {
        try {
            return Timestamp.from(Instant.parse(ts));
        } catch (Exception e) {
            return Timestamp.from(Instant.now());
        }
    }

    private String safe(String v) {
        return v == null ? "" : v.replace("\"", "");
    }

    @PreDestroy
    public void stop() {
        if (writerPool != null) {
            writerPool.shutdownNow();
        }
    }
}
