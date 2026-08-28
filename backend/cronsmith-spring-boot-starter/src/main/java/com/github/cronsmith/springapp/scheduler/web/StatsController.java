package com.github.cronsmith.springapp.scheduler.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry;

/**
 * Aggregate counts for the Dashboard: total tasks, tasks per status, and executor counts.
 *
 * @Description: StatsController
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
@RestController
@RequestMapping("/stats")
public class StatsController {

    private final TaskManager taskManager;
    private final ExecutorRegistry registry;
    private final long executorTtlMillis;

    public StatsController(TaskManager taskManager, ExecutorRegistry registry,
            long executorTtlMillis) {
        this.taskManager = taskManager;
        this.registry = registry;
        this.executorTtlMillis = executorTtlMillis;
    }

    @GetMapping
    public Map<String, Object> stats() {
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            byStatus.put(status.name(), taskManager.getTaskCount(TaskQuery.newQuery().statuses(status)));
        }
        long now = System.currentTimeMillis();
        int executorsTotal = registry.snapshot().size();
        long executorsLive = registry.snapshot().stream()
                .filter(e -> now - e.lastSeen() <= executorTtlMillis).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskTotal", taskManager.getTaskCount(TaskQuery.newQuery()));
        out.put("tasksByStatus", byStatus);
        out.put("executorsTotal", executorsTotal);
        out.put("executorsLive", executorsLive);
        return out;
    }

}
