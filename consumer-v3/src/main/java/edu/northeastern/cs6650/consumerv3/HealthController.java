package edu.northeastern.cs6650.consumerv3;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {
    private final QueueConsumerRunnerV3 runner;
    private final PersistenceService persistenceService;

    public HealthController(QueueConsumerRunnerV3 runner, PersistenceService persistenceService) {
        this.runner = runner;
        this.persistenceService = persistenceService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new HashMap<>();
        m.put("status", "UP");
        m.put("messagesConsumed", runner.getConsumed());
        m.put("broadcastErrors", runner.getBroadcastErrors());
        m.put("enqueueErrors", runner.getEnqueueErrors());
        m.put("dbPersisted", persistenceService.getPersistedCount());
        m.put("dbDeadLettered", persistenceService.getDeadLetterCount());
        m.put("dbQueueSize", persistenceService.getQueueSize());
        return m;
    }
}
