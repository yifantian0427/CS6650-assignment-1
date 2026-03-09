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
        int batchSize = 10;
        int delayMs = 50;
        int numMessages = 500000;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--serverUrl":
                case "-s":
                    if (i + 1 < args.length)
                        baseUrl = args[++i];
                    break;
                case "--rooms":
                    if (i + 1 < args.length)
                        numRooms = Integer.parseInt(args[++i]);
                    break;
                case "--senders":
                    if (i + 1 < args.length)
                        numSenders = Integer.parseInt(args[++i]);
                    break;
                case "--messages":
                    if (i + 1 < args.length)
                        numMessages = Integer.parseInt(args[++i]);
                    break;
                case "--batch":
                case "-b":
                    if (i + 1 < args.length)
                        batchSize = Integer.parseInt(args[++i]);
                    break;
                case "--delay":
                case "-d":
                    if (i + 1 < args.length)
                        delayMs = Integer.parseInt(args[++i]);
                    break;
            }
        }
        // Normalize: ensure baseUrl ends with /chat (no room suffix)
        baseUrl = baseUrl.replaceAll("/$", "");
        if (!baseUrl.endsWith("/chat"))
            baseUrl = baseUrl + "/chat";

        System.out.println("=== PART2 MAIN START ===");
        System.out
                .println(String.format("Server: %s | Rooms: %d | Senders: %d | Messages: %d | Batch: %d | Delay: %dms",
                        baseUrl, numRooms, numSenders, numMessages, batchSize, delayMs));

        // Metrics pipeline
        BlockingQueue<MetricRow> metricsQ = new LinkedBlockingQueue<>();
        AtomicBoolean csvDone = new AtomicBoolean(false);
        String csvPath = "results_part2.csv";
        Thread csvWriter = new Thread(new CsvWriter(metricsQ, csvDone, csvPath));
        csvWriter.start();

        LatencyTracker latencyTracker = new LatencyTracker(metricsQ);

        // One queue per room
        List<BlockingQueue<ChatMessage>> roomQueues = new ArrayList<>(numRooms);
        for (int i = 0; i < numRooms; i++)
            roomQueues.add(new LinkedBlockingQueue<>());

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
                    latencyTracker,
                    batchSize,
                    delayMs));
            t.start();
            senders.add(t);
        }

        final int totalMessagesToDeliver = numMessages;
        // Produce messages into the correct room queue
        Thread generator = new Thread(
                new MessageGenerator(roomQueues, numRooms, totalMessagesToDeliver, doneProducing));
        generator.start();

        // Progress monitor
        Thread monitor = new Thread(() -> {
            try {
                while (!doneProducing.get() || roomQueues.stream().anyMatch(q -> !q.isEmpty())) {
                    Thread.sleep(2000);
                    int success = SenderWorker.totalSuccess.get();
                    int retries = SenderWorker.totalRetries.get();
                    System.out.println(String.format("[Progress] Sent: %d / %d | Retries: %d", success,
                            totalMessagesToDeliver, retries));
                    if (success >= totalMessagesToDeliver)
                        break;
                }
            } catch (InterruptedException ignored) {
            }
        });
        monitor.setDaemon(true);
        monitor.start();

        generator.join();
        for (Thread t : senders)
            t.join();

        long end = System.currentTimeMillis();

        csvDone.set(true);
        csvWriter.join();

        System.out.println("=== PART2 MAIN DONE ===");
        System.out.println("Wall time (ms): " + (end - start));
        System.out.println("CSV written: " + csvPath);
    }
}
