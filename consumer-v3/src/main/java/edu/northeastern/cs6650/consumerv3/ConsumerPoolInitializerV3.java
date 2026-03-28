package edu.northeastern.cs6650.consumerv3;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsumerPoolInitializerV3 implements ApplicationRunner {
    private final QueueConsumerRunnerV3 runner;

    public ConsumerPoolInitializerV3(QueueConsumerRunnerV3 runner) {
        this.runner = runner;
    }

    @Override
    public void run(ApplicationArguments args) {
        runner.start();
    }
}
