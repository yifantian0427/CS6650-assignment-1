package edu.northeastern.cs6650.server;

import java.time.Instant;

public class ChatValidator {

    public static String validate(ChatMessage msg) {

        if (msg.userId < 1 || msg.userId > 100000)
            return "Invalid userId";

        if (msg.username == null ||
                !msg.username.matches("^[A-Za-z0-9]{3,20}$"))
            return "Invalid username";

        if (msg.message == null ||
                msg.message.length() < 1 ||
                msg.message.length() > 500)
            return "Invalid message length";

        try {
            Instant.parse(msg.timestamp);
        } catch (Exception e) {
            return "Invalid timestamp";
        }

        if (!("TEXT".equals(msg.messageType) ||
                "JOIN".equals(msg.messageType) ||
                "LEAVE".equals(msg.messageType)))
            return "Invalid messageType";

        return null;
    }
}
