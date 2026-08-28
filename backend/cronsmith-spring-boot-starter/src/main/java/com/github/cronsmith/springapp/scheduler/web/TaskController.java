package com.github.cronsmith.springapp.scheduler.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.HttpApiCustomTask;
import com.github.cronsmith.springapp.scheduler.HttpDispatchCustomTask;
import com.github.cronsmith.springapp.scheduler.TaskSaveRequest;
import com.github.cronsmith.utils.StringUtils;

/**
 * Task CRUD over the task manager. Reads are served locally; writes (save, delete) are routed to the
 * leader by the task manager.
 *
 * @Description: TaskController
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskManager taskManager;
    private final java.time.ZoneId zoneId;

    public TaskController(TaskManager taskManager, java.time.ZoneId zoneId) {
        this.taskManager = taskManager;
        this.zoneId = zoneId;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String group,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String taskClass,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        TaskQuery query = TaskQuery.newQuery();
        if (StringUtils.isNotBlank(group)) {
            query.group(group);
        }
        if (StringUtils.isNotBlank(name)) {
            query.name(name);
        }
        if (StringUtils.isNotBlank(taskClass)) {
            query.taskClass(taskClass);
        }
        if (StringUtils.isNotBlank(status)) {
            List<TaskStatus> parsed = new ArrayList<>();
            for (String s : status.split(",")) {
                TaskStatus one = TaskStatus.forName(s.trim());
                if (one != null) {
                    parsed.add(one);
                }
            }
            if (!parsed.isEmpty()) {
                query.statuses(parsed.toArray(new TaskStatus[0]));
            }
        }
        if (limit > 0) {
            query.limit(limit);
        }
        if (offset > 0) {
            query.offset(offset);
        }
        List<TaskDetailView> items = new ArrayList<>();
        for (TaskDetail detail : taskManager.findTaskDetails(query)) {
            items.add(TaskDetailView.of(detail));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", taskManager.getTaskCount(query));
        response.put("items", items);
        return response;
    }

    @GetMapping("/{group}/{name}")
    public ResponseEntity<TaskDetailView> get(@PathVariable String group,
            @PathVariable String name) {
        TaskDetail detail = taskManager.getTaskDetail(TaskId.of(group, name), false);
        return detail != null ? ResponseEntity.ok(TaskDetailView.of(detail))
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{group}/{name}")
    public ResponseEntity<Void> delete(@PathVariable String group, @PathVariable String name) {
        TaskDetail removed = taskManager.removeTask(TaskId.of(group, name));
        return removed != null ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/{group}/{name}/logs")
    public List<LogView> logs(@PathVariable String group, @PathVariable String name,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        List<LogView> views = new ArrayList<>();
        for (TaskExecutionLog log : taskManager.findExecutionLogs(TaskId.of(group, name), limit,
                offset)) {
            views.add(LogView.of(log));
        }
        return views;
    }

    @PostMapping
    public TaskDetailView save(@RequestBody TaskSaveRequest request,
            @RequestParam(required = false) String application) {
        TaskId taskId = TaskId.of(request.taskGroup(), request.taskName());
        if (request.isHttp()) {
            // An HTTP-API task runs on the scheduler node itself; no executor is involved.
            taskManager.saveTask(HttpApiCustomTask.fromRequest(request), null);
        } else {
            String app = StringUtils.isNotBlank(application) ? application : request.taskGroup();
            taskManager.saveTask(
                    HttpDispatchCustomTask.fromMetadata(request.toExecutorMetadata(), app), null);
        }
        // Give it a first fire time so the leader's windowed claim loop schedules it.
        taskManager.computeNextFiredDateTime(taskId, java.time.LocalDateTime.now(zoneId));
        return TaskDetailView.of(taskManager.getTaskDetail(taskId, true));
    }

    /** Hold a task: it keeps its state but will not fire until resumed. */
    @PostMapping("/{group}/{name}/pause")
    public ResponseEntity<TaskDetailView> pause(@PathVariable String group,
            @PathVariable String name) {
        return transition(TaskId.of(group, name), TaskStatus.PAUSED, false);
    }

    /** Put a paused task back in service, at its next fire time from now. */
    @PostMapping("/{group}/{name}/resume")
    public ResponseEntity<TaskDetailView> resume(@PathVariable String group,
            @PathVariable String name) {
        return transition(TaskId.of(group, name), TaskStatus.STANDBY, true);
    }

    /** Withdraw a task: it stops firing but its history stays readable. */
    @PostMapping("/{group}/{name}/cancel")
    public ResponseEntity<TaskDetailView> cancel(@PathVariable String group,
            @PathVariable String name) {
        return transition(TaskId.of(group, name), TaskStatus.CANCELED, false);
    }

    /** Run the task once, right now, via a live executor — independent of its schedule. Blocks for
     *  the result and records it in the execution history. */
    @PostMapping("/{group}/{name}/run")
    public ResponseEntity<Map<String, Object>> runNow(@PathVariable String group,
            @PathVariable String name) {
        TaskId taskId = TaskId.of(group, name);
        TaskDetail detail = taskManager.getTaskDetail(taskId, false);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        java.time.LocalDateTime firedAt = java.time.LocalDateTime.now(zoneId);
        long start = System.currentTimeMillis();
        TaskExecutionLog log = new TaskExecutionLog(taskId, firedAt).attempt(0)
                .parameter(detail.getInitialParameter());
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Object result = detail.getTask().execute(detail.getInitialParameter());
            long elapsed = System.currentTimeMillis() - start;
            log.success(true).returnValue(result != null ? result.toString() : null).elapsed(elapsed);
            response.put("success", true);
            response.put("returnValue", result);
            response.put("elapsed", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.success(false).error(e).elapsed(elapsed);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("elapsed", elapsed);
        }
        taskManager.recordExecution(log);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<TaskDetailView> transition(TaskId taskId, TaskStatus target,
            boolean recomputeFireTime) {
        if (taskManager.getTaskDetail(taskId, false) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!taskManager.setTaskStatus(taskId, target)) {
            // The status state machine rejected the transition (e.g. resuming a task that is not paused).
            return ResponseEntity.status(409).build();
        }
        if (recomputeFireTime) {
            taskManager.computeNextFiredDateTime(taskId, java.time.LocalDateTime.now(zoneId));
        }
        return ResponseEntity.ok(TaskDetailView.of(taskManager.getTaskDetail(taskId, true)));
    }

}
