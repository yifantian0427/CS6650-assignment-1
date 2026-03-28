package edu.northeastern.cs6650.consumerv3;

import edu.northeastern.cs6650.consumerv3.dto.QueueMessage;

public class PersistRequest {
    private final QueueMessage message;
    private final int attempt;

    public PersistRequest(QueueMessage message, int attempt) {
        this.message = message;
        this.attempt = attempt;
    }

    public QueueMessage getMessage() { return message; }
    public int getAttempt() { return attempt; }
}
