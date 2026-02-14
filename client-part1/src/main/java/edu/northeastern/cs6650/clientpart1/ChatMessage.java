package edu.northeastern.cs6650.clientpart1;

public class ChatMessage {
    public int userId;
    public String username;
    public String message;
    public String timestamp;   // ISO-8601
    public String messageType; // TEXT/JOIN/LEAVE
    public int roomId;         // used to pick ws://.../chat/{roomId}
}
