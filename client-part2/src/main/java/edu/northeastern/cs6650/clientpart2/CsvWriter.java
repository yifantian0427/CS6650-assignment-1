package edu.northeastern.cs6650.clientpart2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CsvWriter implements Runnable {

    private final BlockingQueue<MetricRow> queue;
    private final AtomicBoolean done;
    private final String path;

    public CsvWriter(BlockingQueue<MetricRow> queue, AtomicBoolean done, String path) {
        this.queue = queue;
        this.done = done;
        this.path = path;
    }

    @Override
    public void run() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write("timestamp,messageType,latency,statusCode,roomId\n");

            while (true) {
                MetricRow row = queue.poll(500, TimeUnit.MILLISECONDS);
                if (row == null) {
                    if (done.get() && queue.isEmpty()) break;
                    continue;
                }
                bw.write(row.timestampMs + "," + row.messageType + "," + row.latencyMs + "," + row.statusCode + "," + row.roomId + "\n");
            }
            bw.flush();
        } catch (Exception e) {
            System.out.println("CSV writer error: " + e.getMessage());
        }
    }
}
