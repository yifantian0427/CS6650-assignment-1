package edu.northeastern.cs6650.server.queue;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple circuit breaker for queue operations: opens after N consecutive failures,
 * allows retry after a cooldown period.
 */
@Component
public class CircuitBreaker {

    private final QueueConfig config;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);

    public CircuitBreaker(QueueConfig config) {
        this.config = config;
    }

    /** Returns true if the circuit allows a call (closed or half-open). */
    public boolean allowRequest() {
        int failures = consecutiveFailures.get();
        if (failures < config.getCircuitOpenThreshold()) {
            return true;
        }
        long openTime = openedAt.get();
        if (openTime == 0) {
            openedAt.compareAndSet(0, System.currentTimeMillis());
        }
        return System.currentTimeMillis() - openedAt.get() >= config.getCircuitRetryMs();
    }

    /** Call after a successful queue operation to reset the circuit. */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        openedAt.set(0);
    }

    /** Call after a failed queue operation. */
    public void recordFailure() {
        int prev = consecutiveFailures.getAndIncrement();
        if (prev == 0) {
            openedAt.set(System.currentTimeMillis());
        }
    }
}
