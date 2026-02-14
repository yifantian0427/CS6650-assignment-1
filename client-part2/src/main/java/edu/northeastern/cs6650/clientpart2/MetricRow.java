package edu.northeastern.cs6650.clientpart2;

public class MetricRow {
    public final long timestampMs;
    public final String messageType;
    public final long latencyMs;
    public final int statusCode;  // 200=success, 400=server error, 408=timeout, 500=client error
    public final int roomId;

    public MetricRow(long timestampMs, String messageType, long latencyMs, int statusCode, int roomId) {
        this.timestampMs = timestampMs;
        this.messageType = messageType;
        this.latencyMs = latencyMs;
        this.statusCode = statusCode;
        this.roomId = roomId;
    }
}
