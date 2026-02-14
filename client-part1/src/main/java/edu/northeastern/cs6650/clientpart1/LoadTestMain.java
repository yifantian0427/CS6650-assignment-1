package edu.northeastern.cs6650.clientpart1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadTestMain {

    public static void main(String[] args) throws Exception {
        String baseUrl = "ws://localhost:8080/chat/"; // change to EC2 later

        // -------------------------
        // WARMUP PHASE (required)
        // -------------------------
        int warmupThreads = 32;
        int perThreadMessages = 1000;

        Metrics warmup = new Metrics();
        warmup.start();

        List<Thread> warmupWorkers = new ArrayList<>();
        for (int i = 0; i < warmupThreads; i++) {
            int roomId = 1 + (i % 20); // spread across rooms 1..20
            Thread t = new Thread(new WarmupWorker(baseUrl, roomId, perThreadMessages, warmup));
            warmupWorkers.add(t);
            t.start();
        }
        for (Thread t : warmupWorkers) t.join();

        warmup.end();

        System.out.println("=== WARMUP DONE ===");
        System.out.println("Success: " + warmup.success.get());
        System.out.println("Failed: " + warmup.failed.get());
        System.out.println("Connections: " + warmup.connections.get());
        System.out.println("Wall time (ms): " + warmup.wallTimeMs());
        System.out.println("Throughput (msg/s): " + warmup.throughputPerSec());

        // -------------------------
        // MAIN PHASE (required)
        // -------------------------
        int totalMainMessages = 500_000;  // assignment requirement
        int senderThreads = 100;         // good starting point locally; tune later

        // Producer/consumer queue (bounded)
        BlockingQueue<ChatMessage> queue = new ArrayBlockingQueue<>(20_000);

        // Signal when generator is done
        AtomicBoolean doneProducing = new AtomicBoolean(false);

        Metrics main = new Metrics();

        // Single dedicated generator thread (required)
        Thread producer = new Thread(() -> {
            new MessageGenerator(queue, totalMainMessages).run();
            doneProducing.set(true);
        });

        // Sender threads keep persistent WS connections where possible (required)
        List<Thread> senders = new ArrayList<>();
        for (int i = 0; i < senderThreads; i++) {
            Thread t = new Thread(new SenderWorker(baseUrl, queue, main, doneProducing));
            senders.add(t);
        }

        System.out.println("=== MAIN PHASE START ===");
        main.start();

        producer.start();
        for (Thread t : senders) t.start();

        Thread progress = new Thread(() -> {
            while (true) {
                try { Thread.sleep(2000); }
                catch (InterruptedException e) { return; }

                int done = main.success.get() + main.failed.get();
                System.out.println(
                        "Progress: " + done + "/" + totalMainMessages +
                                " (success=" + main.success.get() +
                                ", fail=" + main.failed.get() + ")"
                );

                if (done >= totalMainMessages) return;
            }
        });
        progress.setDaemon(true);
        progress.start();

        producer.join();
        for (Thread t : senders) t.join();

        main.end();

        System.out.println("=== MAIN DONE ===");
        System.out.println("Success: " + main.success.get());
        System.out.println("Failed: " + main.failed.get());
        System.out.println("Connections: " + main.connections.get());
        System.out.println("Wall time (ms): " + main.wallTimeMs());
        System.out.println("Throughput (msg/s): " + main.throughputPerSec());
        System.out.println("Total accounted (success+failed): " + (main.success.get() + main.failed.get()));
    }
}
