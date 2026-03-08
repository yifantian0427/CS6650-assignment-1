package edu.northeastern.cs6650.server;

import edu.northeastern.cs6650.server.queue.ChannelPool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ChannelPool channelPool;

    public HealthController(ChannelPool channelPool) {
        this.channelPool = channelPool;
    }

    @GetMapping("/health")
    public String health() {
        if (channelPool.isAvailable()) {
            return "Server is running";
        }
        return "Server running; queue unavailable";
    }
}
