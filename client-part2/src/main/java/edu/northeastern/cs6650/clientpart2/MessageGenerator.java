package edu.northeastern.cs6650.clientpart2;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageGenerator implements Runnable {

    private final List<BlockingQueue<ChatMessage>> roomQueues;
    private final int numMessages;
    private final AtomicBoolean doneProducing;

    private final int numRooms;
    private final Random rand = new Random();

    public MessageGenerator(List<BlockingQueue<ChatMessage>> roomQueues,
                            int numRooms,
                            int numMessages,
                            AtomicBoolean doneProducing) {
        this.roomQueues = roomQueues;
        this.numRooms = numRooms;
        this.numMessages = numMessages;
        this.doneProducing = doneProducing;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < numMessages; i++) {
                int roomId = 1 + rand.nextInt(numRooms);

                ChatMessage msg = new ChatMessage();
                msg.clientTimestampMs = System.currentTimeMillis();
                msg.userId = 1000 + rand.nextInt(1000);
                msg.username = "user" + msg.userId;
                msg.message = "hello-" + i;
                msg.messageType = "TEXT";
                msg.roomId = roomId;
                msg.timestamp = Instant.now().toString();

                // push into the correct room queue (roomId starts at 1)
                roomQueues.get(roomId - 1).put(msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            doneProducing.set(true);
        }
    }
}
