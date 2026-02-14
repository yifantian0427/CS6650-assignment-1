package edu.northeastern.cs6650.clientpart2;

public class ChatMessage {

    public long clientTimestampMs;   // used as requestId
    public int userId;
    public String username;
    public String message;
    public String messageType;       // TEXT
    public int roomId;
    /** ISO-8601 timestamp required by server for validation */
    public String timestamp;
}
