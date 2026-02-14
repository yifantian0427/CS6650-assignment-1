package edu.northeastern.cs6650.clientpart2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadTestMain {

    public static void main(String[] args) throws Exception {
        System.out.println("=== PART2 MAIN START ===");

        String baseUrl = "ws://localhost:8080/chat";

        int numRooms = 100;        // rooms 1..100
        int numSenders = 100;      // one sender per room (recommended)
        int numMessages = 500000;

        // Metrics pipeline
        BlockingQueue<MetricRow> metricsQ = new LinkedBlockingQueue<>();
        AtomicBoolean csvDone = new AtomicBoolean(false);
        String csvPath = "results_part2.csv";
        Thread csvWriter = new Thread(new CsvWriter(metricsQ, csvDone, csvPath));
        csvWriter.start();

        LatencyTracker latencyTracker = new LatencyTracker(metricsQ);

        // One queue per room
        List<BlockingQueue<ChatMessage>> roomQueues = new ArrayList<>(numRooms);
        for (int i = 0; i < numRooms; i++) roomQueues.add(new LinkedBlockingQueue<>());

        AtomicBoolean doneProducing = new AtomicBoolean(false);

        long start = System.currentTimeMillis();

        // Start senders: sender i handles roomId = i+1
        List<Thread> senders = new ArrayList<>();
        for (int i = 0; i < numSenders; i++) {
            int roomId = (i % numRooms) + 1;
            Thread t = new Thread(new SenderWorker(
                    baseUrl,
                    roomId,
                    roomQueues.get(roomId - 1),
                    doneProducing,
                    latencyTracker
            ));
            t.start();
            senders.add(t);
        }

        // Produce messages into the correct room queue
        Thread generator = new Thread(new MessageGenerator(roomQueues, numRooms, numMessages, doneProducing));
        generator.start();

        generator.join();
        for (Thread t : senders) t.join();

        long end = System.currentTimeMillis();

        csvDone.set(true);
        csvWriter.join();

        System.out.println("=== PART2 MAIN DONE ===");
        System.out.println("Wall time (ms): " + (end - start));
        System.out.println("CSV written: " + csvPath);
    }
}
