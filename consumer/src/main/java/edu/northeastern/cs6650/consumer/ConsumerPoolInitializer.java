package edu.northeastern.cs6650.consumer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsumerPoolInitializer implements ApplicationRunner {

    private final QueueConsumerRunner queueConsumerRunner;

    public ConsumerPoolInitializer(QueueConsumerRunner queueConsumerRunner) {
        this.queueConsumerRunner = queueConsumerRunner;
    }

    @Override
    public void run(ApplicationArguments args) {
        queueConsumerRunner.start();
    }
}
