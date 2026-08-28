package com.github.cronsmith.springapp.scheduler.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.util.StringUtils;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.ClusterExecutorRegistry;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry;
import com.github.cronsmith.springapp.scheduler.ExecutorTaskMetadata;
import com.github.cronsmith.springapp.scheduler.HeartbeatRequest;
import com.github.cronsmith.springapp.scheduler.HttpDispatchCustomTask;
import com.github.cronsmith.springapp.scheduler.RegistrationRequest;
import com.github.cronsmith.springapp.scheduler.RegistrationResponse;

/**
 * Where executors register and heartbeat. Registration saves (saveOrUpdate) each task through the
 * task manager — which routes the write to the leader — and records the executor; a heartbeat only
 * refreshes the executor.
 *
 * @Description: ExecutorController
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@RestController
public class ExecutorController {

    private final TaskManager taskManager;
    private final ClusterExecutorRegistry executors;
    private final ExecutorRegistry registry;
    private final long executorTtlMillis;
    private final ZoneId zoneId;

    public ExecutorController(TaskManager taskManager, ClusterExecutorRegistry executors,
            ExecutorRegistry registry, long executorTtlMillis, ZoneId zoneId) {
        this.taskManager = taskManager;
        this.executors = executors;
        this.registry = registry;
        this.executorTtlMillis = executorTtlMillis;
        this.zoneId = zoneId;
    }

    /** The registered executors, each with its last-heartbeat time and whether it is still live. */
    @GetMapping("/executors")
    public List<Map<String, Object>> list() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExecutorRegistry.ExecutorInstance e : registry.snapshot()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("application", e.application());
            m.put("instanceId", e.instanceId());
            m.put("runUrl", e.runUrl());
            m.put("healthCheckUrl", e.healthCheckUrl());
            m.put("weight", e.weight());
            m.put("lastSeen",
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(e.lastSeen()), zoneId).toString());
            m.put("healthy", now - e.lastSeen() <= executorTtlMillis);
            out.add(m);
        }
        return out;
    }

    @PostMapping("/executors/register")
    public RegistrationResponse register(@RequestBody RegistrationRequest request) {
        // The scheduler is the authority for instance identity: mint one on first registration (blank
        // id), otherwise keep the id the executor was already given so its registry entry is reused.
        String instanceId = StringUtils.hasText(request.instanceId()) ? request.instanceId()
                : UUID.randomUUID().toString();
        if (request.tasks() != null) {
            for (ExecutorTaskMetadata metadata : request.tasks()) {
                TaskId taskId = TaskId.of(metadata.taskGroup(), metadata.taskName());
                TaskDetail existing = taskManager.getTaskDetail(taskId, false);
                // Several instances of one application register the same tasks. Only save when the
                // task is new or its definition changed, so a sibling instance starting up does not
                // reset an already-scheduled task back to standby.
                if (existing == null || !sameDefinition(existing, metadata, request.application())) {
                    taskManager.saveTask(
                            HttpDispatchCustomTask.fromMetadata(metadata, request.application()), null);
                    // Give the task its first fire time (routed to the leader), so the leader's
                    // windowed claim loop picks it up when it comes due — no matter which node the
                    // registration landed on.
                    taskManager.computeNextFiredDateTime(taskId, LocalDateTime.now(zoneId));
                }
            }
        }
        executors.register(request.application(), instanceId, request.runUrl(),
                request.healthCheckUrl(), request.weightOrDefault());
        return new RegistrationResponse(instanceId);
    }

    private boolean sameDefinition(TaskDetail existing, ExecutorTaskMetadata metadata,
            String application) {
        try {
            Task task = existing.getTask();
            if (!(task instanceof HttpDispatchCustomTask stored)) {
                return false;
            }
            return Objects.equals(stored.getTaskClassName(), metadata.className())
                    && Objects.equals(stored.getTaskMethodName(), metadata.methodName())
                    && Objects.equals(stored.getBeanName(), metadata.beanName())
                    && Objects.equals(stored.getApplication(), application)
                    && Objects.equals(existing.getInitialParameter(), metadata.initialParameter())
                    && stored.getTimeout() == metadata.timeout()
                    && stored.getMaxRetryCount() == metadata.maxRetryCount()
                    && stored.getRetryInterval() == metadata.retryInterval()
                    && Objects.equals(nameOf(stored), metadata.misfirePolicy())
                    && Objects.equals(cronOf(task), metadata.cron());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String nameOf(Task task) {
        return task.getMisfirePolicy() != null ? task.getMisfirePolicy().name() : null;
    }

    private static String cronOf(Task task) {
        return task.getCronExpression() != null ? task.getCronExpression().toString() : null;
    }

    @PostMapping("/executors/heartbeat")
    public void heartbeat(@RequestBody HeartbeatRequest request) {
        executors.register(request.application(), request.instanceId(), request.runUrl(),
                request.healthCheckUrl(), request.weightOrDefault());
    }

}
