package edu.northeastern.cs6650.clientpart2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadTestMain {

    private static final String DEFAULT_URL = "ws://localhost:8080/chat";

    public static void main(String[] args) throws Exception {
        String baseUrl = DEFAULT_URL;
        int numRooms = 20;
        int numSenders = 20;
        int numMessages = 2000;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--serverUrl":
                case "-s":
                    if (i + 1 < args.length) baseUrl = args[++i];
                    break;
                case "--rooms":
                    if (i + 1 < args.length) numRooms = Integer.parseInt(args[++i]);
                    break;
                case "--senders":
                    if (i + 1 < args.length) numSenders = Integer.parseInt(args[++i]);
                    break;
                case "--messages":
                    if (i + 1 < args.length) numMessages = Integer.parseInt(args[++i]);
                    break;
            }
        }
        // Normalize: ensure baseUrl ends with /chat (no room suffix)
        baseUrl = baseUrl.replaceAll("/$", "");
        if (!baseUrl.endsWith("/chat")) baseUrl = baseUrl + "/chat";

        System.out.println("=== PART2 MAIN START ===");
        System.out.println("Server: " + baseUrl + " | Rooms: " + numRooms + " | Senders: " + numSenders + " | Messages: " + numMessages);

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
