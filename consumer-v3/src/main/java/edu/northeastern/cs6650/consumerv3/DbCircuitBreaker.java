package edu.northeastern.cs6650.consumerv3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DbCircuitBreaker {
    private final int openThreshold;
    private final long retryMs;
    private final AtomicInteger failures = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);

    public DbCircuitBreaker(
            @Value("${app.db.circuit-open-threshold:5}") int openThreshold,
            @Value("${app.db.circuit-retry-ms:10000}") long retryMs) {
        this.openThreshold = openThreshold;
        this.retryMs = retryMs;
    }

    public boolean allowRequest() {
        if (failures.get() < openThreshold) return true;
        long t = openedAt.get();
        if (t == 0) openedAt.compareAndSet(0, System.currentTimeMillis());
        return System.currentTimeMillis() - openedAt.get() >= retryMs;
    }

    public void recordSuccess() {
        failures.set(0);
        openedAt.set(0);
    }

    public void recordFailure() {
        int prev = failures.getAndIncrement();
        if (prev == 0) openedAt.set(System.currentTimeMillis());
    }
}
