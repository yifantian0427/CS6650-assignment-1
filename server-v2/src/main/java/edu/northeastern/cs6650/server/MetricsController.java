package edu.northeastern.cs6650.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    private final MetricsQueryService metricsQueryService;

    public MetricsController(MetricsQueryService metricsQueryService) {
        this.metricsQueryService = metricsQueryService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(defaultValue = "1") String roomId,
            @RequestParam(defaultValue = "1000") String userId,
            @RequestParam(defaultValue = "") String startTime,
            @RequestParam(defaultValue = "") String endTime,
            @RequestParam(defaultValue = "10") int topN) {
        return metricsQueryService.fetchMetrics(roomId, userId, startTime, endTime, topN);
    }
}
