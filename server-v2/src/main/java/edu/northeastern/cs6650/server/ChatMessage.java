package edu.northeastern.cs6650.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {

    public int userId;
    public String username;
    public String message;
    public String timestamp;
    public String messageType;
}
