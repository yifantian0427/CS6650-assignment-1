package edu.northeastern.cs6650.consumer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Same structure as server-v2 QueueMessage for deserialization. */
public class QueueMessage {

    @JsonProperty("messageId")
    public String messageId;

    @JsonProperty("roomId")
    public String roomId;

    @JsonProperty("userId")
    public String userId;

    @JsonProperty("username")
    public String username;

    @JsonProperty("message")
    public String message;

    @JsonProperty("timestamp")
    public String timestamp;

    @JsonProperty("messageType")
    public String messageType;

    @JsonProperty("serverId")
    public String serverId;

    @JsonProperty("clientIp")
    public String clientIp;
}
