package edu.northeastern.cs6650.clientpart1;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class MessageGenerator implements Runnable {
    private final BlockingQueue<ChatMessage> queue;
    private final int total;
    private final Random rand = new Random();

    // TODO: expand to 50 messages later
    private final List<String> pool = java.util.stream.IntStream.range(0, 50)
            .mapToObj(i -> "message-" + i)
            .toList();

    public MessageGenerator(BlockingQueue<ChatMessage> queue, int total) {
        this.queue = queue;
        this.total = total;
    }

    @Override
    public void run() {
        for (int i = 0; i < total; i++) {
            ChatMessage m = new ChatMessage();
            m.userId = 1 + rand.nextInt(100000);
            m.username = "user" + m.userId;
            m.message = pool.get(rand.nextInt(pool.size()));
            m.roomId = 1 + rand.nextInt(20);
            m.timestamp = Instant.now().toString();

            int r = rand.nextInt(100);
            m.messageType = (r < 90) ? "TEXT" : (r < 95 ? "JOIN" : "LEAVE");

            try {
                queue.put(m);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
