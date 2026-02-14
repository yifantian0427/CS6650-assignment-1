package edu.northeastern.cs6650.clientpart2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class StatsCalculator {

    public static void main(String[] args) throws Exception {
        String csvPath = (args.length > 0) ? args[0] : "results_part2.csv";

        List<Double> latencies = new ArrayList<>(600_000);

        Map<Integer, Integer> roomCount = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();
        Map<Long, Integer> bucket10s = new TreeMap<>(); // bucketIndex -> count

        long firstTs = -1;
        long lastTs = -1;

        int totalRows = 0;
        int okRows = 0;
        int timeoutRows = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine(); // skip
            if (header == null) throw new RuntimeException("Empty CSV: " + csvPath);

            String line;
            while ((line = br.readLine()) != null) {
                totalRows++;
                // timestamp,messageType,latency,statusCode,roomId
                String[] parts = line.split(",");
                if (parts.length < 5) continue;

                long ts = Long.parseLong(parts[0]);
                String msgType = parts[1];
                double latency = Double.parseDouble(parts[2]);
                int statusCode = Integer.parseInt(parts[3]);
                int roomId = Integer.parseInt(parts[4]);

                if (firstTs < 0) firstTs = ts;
                lastTs = ts;

                typeCount.put(msgType, typeCount.getOrDefault(msgType, 0) + 1);
                roomCount.put(roomId, roomCount.getOrDefault(roomId, 0) + 1);

                long bucket = (firstTs < 0) ? 0 : ((ts - firstTs) / 10_000L);
                bucket10s.put(bucket, bucket10s.getOrDefault(bucket, 0) + 1);

                if (statusCode == 200 && latency >= 0) {
                    okRows++;
                    latencies.add(latency);
                } else if (statusCode == 408 || latency < 0) {
                    timeoutRows++;
                }
            }
        }

        System.out.println("=== CSV Summary ===");
        System.out.println("File: " + csvPath);
        System.out.println("Total rows: " + totalRows);
        System.out.println("OK rows (status=200): " + okRows);
        System.out.println("Timeout/failed rows: " + timeoutRows);

        if (firstTs > 0 && lastTs > 0) {
            double seconds = (lastTs - firstTs) / 1000.0;
            System.out.println("Duration (s): " + seconds);
            System.out.println("Overall throughput (msg/s): " + (seconds > 0 ? (totalRows / seconds) : 0.0));
        }

        System.out.println("\n=== Message Type Distribution ===");
        for (String k : new TreeSet<>(typeCount.keySet())) {
            System.out.println(k + ": " + typeCount.get(k));
        }

        System.out.println("\n=== Throughput Per Room (counts) ===");
        List<Integer> rooms = new ArrayList<>(roomCount.keySet());
        Collections.sort(rooms);
        for (int r : rooms) {
            System.out.println("Room " + r + ": " + roomCount.get(r));
        }

        if (!latencies.isEmpty()) {
            Collections.sort(latencies);

            double mean = latencies.stream().mapToDouble(x -> x).average().orElse(0.0);
            double min = latencies.get(0);
            double max = latencies.get(latencies.size() - 1);
            double median = percentile(latencies, 50);
            double p95 = percentile(latencies, 95);
            double p99 = percentile(latencies, 99);

            System.out.println("\n=== Latency Stats (ms) [status=200 only] ===");
            System.out.println("Count: " + latencies.size());
            System.out.println("Mean: " + mean);
            System.out.println("Min: " + min);
            System.out.println("Median: " + median);
            System.out.println("P95: " + p95);
            System.out.println("P99: " + p99);
            System.out.println("Max: " + max);
        } else {
            System.out.println("\nNo valid latency samples found (status=200).");
        }

        // write 10s buckets to csv for plotting
        String bucketsPath = "buckets_10s.csv";
        try (FileWriter fw = new FileWriter(bucketsPath)) {
            fw.write("bucketIndex,bucketStartSec,count,throughputPerSec\n");
            for (Map.Entry<Long, Integer> e : bucket10s.entrySet()) {
                long bucket = e.getKey();
                int count = e.getValue();
                long startSec = bucket * 10;
                double tput = count / 10.0;
                fw.write(bucket + "," + startSec + "," + count + "," + tput + "\n");
            }
        }
        System.out.println("\nWrote throughput buckets: " + bucketsPath);
    }

    private static double percentile(List<Double> sorted, int p) {
        if (sorted.isEmpty()) return 0.0;
        double rank = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sorted.get(lo);
        double w = rank - lo;
        return sorted.get(lo) * (1 - w) + sorted.get(hi) * w;
    }
}
