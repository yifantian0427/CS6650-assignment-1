package edu.northeastern.cs6650.server.queue;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures the channel pool is initialized after the application context is ready.
 */
@Configuration
public class QueuePoolInitializer {

    private final ChannelPool channelPool;

    public QueuePoolInitializer(ChannelPool channelPool) {
        this.channelPool = channelPool;
    }

    @PostConstruct
    public void init() {
        channelPool.init();
    }
}
