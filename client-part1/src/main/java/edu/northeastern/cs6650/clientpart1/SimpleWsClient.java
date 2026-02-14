package edu.northeastern.cs6650.clientpart1;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

public class SimpleWsClient extends WebSocketClient {

    private final CountDownLatch openLatch;

    public SimpleWsClient(URI serverUri, CountDownLatch openLatch) {
        super(serverUri);
        this.openLatch = openLatch;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        openLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        // For Part1 we don't need latency yet; just optionally log:
        // System.out.println("RECV: " + message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception ex) {
        // ex.printStackTrace();
    }
}
