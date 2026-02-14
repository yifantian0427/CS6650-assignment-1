package edu.northeastern.cs6650.clientpart2;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;

public class SimpleWsClient extends WebSocketClient {

    private final BlockingQueue<String> inbox;

    public SimpleWsClient(URI serverUri, BlockingQueue<String> inbox) {
        super(serverUri);
        this.inbox = inbox;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("WS connected: " + getURI());
    }

    @Override
    public void onMessage(String message) {
        // THIS IS CRITICAL
        inbox.offer(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("WS closed: " + code + " reason=" + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.out.println("WS error: " + ex.getMessage());
    }
}
