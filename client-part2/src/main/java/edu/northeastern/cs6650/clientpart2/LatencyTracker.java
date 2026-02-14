package edu.northeastern.cs6650.clientpart2;

import java.util.concurrent.BlockingQueue;

public class LatencyTracker {

    private final BlockingQueue<MetricRow> metricsQ;

    public LatencyTracker(BlockingQueue<MetricRow> metricsQ) {
        this.metricsQ = metricsQ;
    }

    /** statusCode: 200 success, 400 server responded ERROR/bad payload, 408 timeout, 500 client exception */
    public void record(long timestampMs, String messageType, long latencyMs, int statusCode, int roomId) {
        metricsQ.offer(new MetricRow(timestampMs, messageType, latencyMs, statusCode, roomId));
    }

    public void recordSuccess(String messageType, long latencyMs, int roomId) {
        record(System.currentTimeMillis(), messageType, latencyMs, 200, roomId);
    }

    public void recordServerError(String messageType, long latencyMs, int roomId) {
        record(System.currentTimeMillis(), messageType, latencyMs, 400, roomId);
    }

    public void recordTimeout(String messageType, int roomId) {
        record(System.currentTimeMillis(), messageType, -1, 408, roomId);
    }

    public void recordClientError(String messageType, int roomId) {
        record(System.currentTimeMillis(), messageType, -1, 500, roomId);
    }
}
