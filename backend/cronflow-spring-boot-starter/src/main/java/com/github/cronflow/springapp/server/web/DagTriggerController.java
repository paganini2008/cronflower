package com.github.cronflow.springapp.server.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.server.CronflowServerProperties;
import com.github.cronflow.springapp.server.DagCoordinator;
import com.github.cronflow.springapp.server.DagExecutorRegistry;
import com.github.cronsmith.springapp.scheduler.CronsmithServerProperties;
import com.github.cronsmith.springapp.scheduler.HttpApiCustomTask;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.utils.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * DAG triggering for the console and for the scheduler itself.
 *
 * <p>
 * Three routes, one engine path (all go through {@link DagCoordinator}, so the flow still runs
 * distributed across the scheduler cluster on the openspreader engine):
 * <ul>
 * <li>{@code POST /dags/{graph}/trigger} — manual, user-authenticated, kicks the graph off once with a
 * caller-supplied initial state.</li>
 * <li>{@code POST /dags/{graph}/fire} — the machine endpoint a scheduled trigger task calls on each
 * fire. It is open (like the executor register / heartbeat endpoints) so a data-only HTTP task can
 * reach it with no user token; the body is the graph's initial state.</li>
 * <li>{@code POST /dags/{graph}/schedule} — registers a cronsmith HTTP task that calls {@code /fire}
 * on the given cron. The task lives in the task list and pauses / resumes / cancels like any other;
 * each fire starts a run.</li>
 * </ul>
 *
 * <p>
 * A scheduled trigger is modelled as an {@link HttpApiCustomTask} rather than a bespoke task class on
 * purpose: only the url-based (HTTP) and bean-based task forms survive the scheduler's store round-trip
 * with their identity and schedule intact, so the HTTP form is what a server-side, executor-free
 * trigger must use.
 *
 * @Description: DagTriggerController
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
@RestController
public class DagTriggerController {

    private final DagExecutorRegistry registry;
    private final DagCoordinator coordinator;
    private final TaskManager taskManager;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final ZoneId zoneId;
    private final String cronflowPrefix;

    public DagTriggerController(DagExecutorRegistry registry, DagCoordinator coordinator,
            TaskManager taskManager, ObjectMapper objectMapper, Environment environment,
            CronsmithServerProperties cronsmithProperties, CronflowServerProperties cronflowProperties) {
        this.registry = registry;
        this.coordinator = coordinator;
        this.taskManager = taskManager;
        this.objectMapper = objectMapper;
        this.environment = environment;
        // Match cronsmith's scheduling zone (default UTC) so a scheduled trigger fires on the same
        // clock as every other task.
        this.zoneId = ZoneId.of(cronsmithProperties.getScheduler().getZone());
        this.cronflowPrefix = normalizePrefix(cronflowProperties.getApiPrefix());
    }

    /**
     * Trigger {@code graph} with an optional JSON body as its initial channel state (manual, from the
     * console).
     *
     * <ul>
     * <li>404 — no graph by that name is registered</li>
     * <li>422 — the graph exists but the engine cannot run it yet</li>
     * <li>200 — accepted; the body carries the {@code runId} to follow the run by</li>
     * </ul>
     */
    @PostMapping("/dags/{graph}/trigger")
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable String graph,
            @RequestBody(required = false) Map<String, Object> initialState) {
        return fireInternal(graph, initialState, "manual");
    }

    /**
     * Machine endpoint: a scheduled trigger task calls this on each fire. Same behaviour as
     * {@code /trigger} but tagged {@code scheduled}, and open to unauthenticated calls (the data-only
     * task carries no user token).
     */
    @PostMapping("/dags/{graph}/fire")
    public ResponseEntity<Map<String, Object>> fire(@PathVariable String graph,
            @RequestBody(required = false) Map<String, Object> initialState) {
        return fireInternal(graph, initialState, "scheduled");
    }

    private ResponseEntity<Map<String, Object>> fireInternal(String graph,
            Map<String, Object> initialState, String triggeredBy) {
        if (registry.definition(graph) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("graph", graph, "error", "no such graph"));
        }
        String runId = coordinator.trigger(graph, initialState == null ? Map.of() : initialState,
                triggeredBy);
        if (runId == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("graph", graph,
                    "error", "graph is not runnable by the engine yet"));
        }
        return ResponseEntity.ok(Map.of("graph", graph, "runId", runId, "triggeredBy", triggeredBy));
    }

    /**
     * Register a cronsmith task that triggers {@code graph} on the given cron. The task shows up in
     * the task list and behaves like any other scheduled task; each fire starts a run.
     *
     * <ul>
     * <li>404 — no graph by that name is registered</li>
     * <li>400 — the request is missing a cron</li>
     * <li>200 — created; the body echoes the task's group / name</li>
     * </ul>
     */
    @PostMapping("/dags/{graph}/schedule")
    public ResponseEntity<Map<String, Object>> schedule(@PathVariable String graph,
            @RequestBody DagScheduleRequest request) {
        if (registry.definition(graph) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("graph", graph, "error", "no such graph"));
        }
        if (StringUtils.isBlank(request.cron())) {
            return ResponseEntity.badRequest().body(Map.of("error", "cron is required"));
        }
        String name = StringUtils.isNotBlank(request.taskName()) ? request.taskName().trim()
                : ("trigger-" + graph);
        String group = StringUtils.isNotBlank(request.taskGroup()) ? request.taskGroup().trim()
                : "cronflow";
        TaskId taskId = TaskId.of(group, name);

        // The task, on each fire, POSTs the seed to this node's own /fire endpoint, which triggers the
        // graph. 127.0.0.1 keeps the call node-local (in Docker every node serves the same internal
        // port, so it works cluster-wide; under run-local's random ports it pins to the creating node).
        String seedBody = seedJson(request.seed());
        String fireUrl = "http://127.0.0.1:" + localPort() + cronflowPrefix + "/dags/" + graph
                + "/fire";

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("taskGroup", group);
        record.put("taskName", name);
        record.put("url", "POST " + fireUrl + " HTTP/1.1");
        record.put("initialParameter", seedBody);
        record.put("description", StringUtils.isNotBlank(request.description())
                ? request.description() : ("Scheduled trigger of DAG " + graph));
        record.put("timeout", request.timeout() != null ? request.timeout() : -1L);
        record.put("maxRetryCount", request.maxRetryCount() != null ? request.maxRetryCount() : 0);
        record.put("retryInterval",
                request.retryInterval() != null ? request.retryInterval() : 1000L);
        record.put("misfirePolicy", StringUtils.isNotBlank(request.misfirePolicy())
                ? request.misfirePolicy() : "FIRE_ONCE_NOW");
        record.put("cron", request.cron().trim());
        record.put("parser", StringUtils.isNotBlank(request.parser()) ? request.parser() : "cron");
        record.put("repeatCount", request.repeatCount() != null ? request.repeatCount() : -1);
        record.put("stopAt",
                StringUtils.isNotBlank(request.stopAt()) ? request.stopAt().trim() : null);

        taskManager.saveTask(new HttpApiCustomTask(record), seedBody);
        // Give it a first fire time so the leader's windowed claim loop schedules it.
        taskManager.computeNextFiredDateTime(taskId, LocalDateTime.now(zoneId));

        return ResponseEntity.ok(Map.of("graph", graph, "taskGroup", group, "taskName", name));
    }

    private String seedJson(Map<String, Object> seed) {
        return objectMapper.writeValueAsString(seed != null ? seed : Map.of());
    }

    private String localPort() {
        String port = environment.getProperty("local.server.port");
        if (StringUtils.isBlank(port)) {
            port = environment.getProperty("server.port", "8080");
        }
        return port;
    }

    private static String normalizePrefix(String prefix) {
        String p = prefix == null ? "" : prefix.trim();
        if (p.isEmpty() || p.equals("/")) {
            return "";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /** Body for {@code POST /dags/{graph}/schedule} — all but the graph (which is in the path). */
    public record DagScheduleRequest(String taskGroup, String taskName, String cron, String parser,
            String description, Map<String, Object> seed, Long timeout, Integer maxRetryCount,
            Long retryInterval, Integer repeatCount, String misfirePolicy, String stopAt) {
    }

}
