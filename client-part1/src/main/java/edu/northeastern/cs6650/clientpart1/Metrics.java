package edu.northeastern.cs6650.clientpart1;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Metrics {
    public final AtomicInteger success = new AtomicInteger(0);
    public final AtomicInteger failed = new AtomicInteger(0);
    public final AtomicInteger connections = new AtomicInteger(0);
    public final AtomicInteger reconnects = new AtomicInteger(0);

    public final AtomicLong startMs = new AtomicLong(0);
    public final AtomicLong endMs = new AtomicLong(0);

    public void start() { startMs.set(System.currentTimeMillis()); }
    public void end() { endMs.set(System.currentTimeMillis()); }

    public long wallTimeMs() { return endMs.get() - startMs.get(); }

    public double throughputPerSec() {
        long ms = wallTimeMs();
        if (ms <= 0) return 0.0;
        return (success.get() * 1000.0) / ms;
    }

    public int totalDone() {
        return success.get() + failed.get();
    }

}
