package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.utils.MapUtils;
import com.github.cronsmith.utils.StringUtils;
/**
 * 
 * Keeps everything in the process. Nothing survives a restart, which makes it the right choice for
 * tests and for schedules that are rebuilt from configuration on every boot, and the wrong one for
 * anything that must not run twice or be forgotten.
 * 
 * <p>
 * Execution logs are capped per task; see {@link #DEFAULT_LOG_CAPACITY}.
 * 
 * @Description: InMemoryTaskManager
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public class InMemoryTaskManager implements TaskManager {

    /** Execution logs kept per task before the oldest are dropped. */
    public static final int DEFAULT_LOG_CAPACITY = 100;

    private final Map<TaskId, InMemoryTaskDetail> taskStore = new ConcurrentHashMap<>();
    private final Map<TaskId, Deque<TaskExecutionLog>> executionLogs = new ConcurrentHashMap<>();
    private final int logCapacity;

    public InMemoryTaskManager() {
        this(DEFAULT_LOG_CAPACITY);
    }

    public InMemoryTaskManager(int logCapacity) {
        this.logCapacity = logCapacity;
    }

    /**
     * 
     * @Description: InMemoryTaskDetail
     * @Author: Fred Feng
     * @Date: 30/03/2025
     * @Version 1.0.0
     */
    static class InMemoryTaskDetail implements TaskDetail {

        private final Task task;
        private String initialParameter;
        private TaskStatus taskStatus;
        private LocalDateTime previousFiredDateTime;
        private LocalDateTime nextFiredDateTime;
        private LocalDateTime lastModified;
        private long runCount;
        private long failureCount;
        private long misfireCount;

        InMemoryTaskDetail(Task task, String initialParameter, TaskStatus taskStatus) {
            this.task = task;
            this.initialParameter = initialParameter;
            this.taskStatus = taskStatus;
            this.lastModified = Settings.now();
        }

        @Override
        public Task getTask() {
            return task;
        }

        @Override
        public synchronized String getInitialParameter() {
            return initialParameter;
        }

        synchronized void setInitialParameter(String initialParameter) {
            this.initialParameter = initialParameter;
            this.lastModified = Settings.now();
        }

        @Override
        public synchronized TaskStatus getTaskStatus() {
            return taskStatus;
        }

        /**
         * @return whether the transition was legal and therefore applied
         */
        synchronized boolean setTaskStatus(TaskStatus target) {
            if (!taskStatus.canTransitionTo(target)) {
                return false;
            }
            this.taskStatus = target;
            this.lastModified = Settings.now();
            return true;
        }

        /**
         * Sets the status without consulting the transition table. Re-saving a task is a fresh
         * registration, and that has to be able to bring a finished or canceled one back.
         */
        synchronized void forceStatus(TaskStatus target) {
            this.taskStatus = target;
            this.lastModified = Settings.now();
        }

        synchronized boolean compareAndSetTaskStatus(TaskStatus expected, TaskStatus target) {
            if (taskStatus != expected) {
                return false;
            }
            return setTaskStatus(target);
        }

        @Override
        public synchronized LocalDateTime getNextFiredDateTime() {
            return nextFiredDateTime;
        }

        @Override
        public synchronized LocalDateTime getPreviousFiredDateTime() {
            return previousFiredDateTime;
        }

        synchronized void setFiredDateTimes(LocalDateTime previous, LocalDateTime next) {
            this.previousFiredDateTime = previous;
            this.nextFiredDateTime = next;
            this.lastModified = Settings.now();
        }

        @Override
        public synchronized LocalDateTime getLastModified() {
            return lastModified;
        }

        @Override
        public synchronized long getRunCount() {
            return runCount;
        }

        @Override
        public synchronized long getFailureCount() {
            return failureCount;
        }

        @Override
        public synchronized long getMisfireCount() {
            return misfireCount;
        }

        synchronized void recordRun(boolean success) {
            runCount++;
            if (!success) {
                failureCount++;
            }
            this.lastModified = Settings.now();
        }

        synchronized void recordMisfire() {
            misfireCount++;
            this.lastModified = Settings.now();
        }

        @Override
        public String toString() {
            return "Task Id: " + task.getTaskId() + ", Task Status: " + getTaskStatus()
                    + ", Previous Fired: " + getPreviousFiredDateTime() + ", Next Fired: "
                    + getNextFiredDateTime();
        }

    }

    @Override
    public TaskDetail saveTask(Task task, String initialParameter) {
        if (task == null) {
            throw new IllegalArgumentException("Task is required");
        }
        TaskId taskId = task.getTaskId();
        String parameter = StringUtils.isNotBlank(initialParameter) ? initialParameter
                : task.getInitialParameter();
        // Anchor the expression to now, so a task saved today does not inherit fire times from
        // whenever its CronBuilder happened to be constructed.
        task.getCronExpression().sync();
        InMemoryTaskDetail existing = taskStore.get(taskId);
        if (existing != null && existing.getTask().getClass() == task.getClass()) {
            // Re-saving is a re-registration: the definition is refreshed and the task goes back to
            // standby, but the counters are history and are kept.
            existing.setInitialParameter(parameter);
            existing.setFiredDateTimes(null, null);
            existing.forceStatus(TaskStatus.STANDBY);
            return existing;
        }
        InMemoryTaskDetail taskDetail =
                new InMemoryTaskDetail(task, parameter, TaskStatus.STANDBY);
        taskStore.put(taskId, taskDetail);
        return taskDetail;
    }

    @Override
    public TaskDetail removeTask(TaskId taskId) {
        executionLogs.remove(taskId);
        return taskStore.remove(taskId);
    }

    @Override
    public TaskDetail getTaskDetail(TaskId taskId, boolean thrown) {
        TaskDetail taskDetail = taskId != null ? taskStore.get(taskId) : null;
        if (taskDetail == null && thrown) {
            throw new TaskDetailNotFoundException(taskId);
        }
        return taskDetail;
    }

    @Override
    public boolean hasTask(TaskId taskId) {
        return taskId != null && taskStore.containsKey(taskId);
    }

    @Override
    public int getTaskCount(TaskQuery query) {
        return (int) filter(query).count();
    }

    @Override
    public List<TaskDetail> findTaskDetails(TaskQuery query) {
        Stream<InMemoryTaskDetail> stream = filter(query)
                .sorted((a, b) -> b.getLastModified().compareTo(a.getLastModified()))
                .skip(query != null ? query.getOffset() : 0);
        if (query != null && query.getLimit() > 0) {
            stream = stream.limit(query.getLimit());
        }
        return stream.collect(Collectors.toList());
    }

    private Stream<InMemoryTaskDetail> filter(TaskQuery query) {
        Stream<InMemoryTaskDetail> stream = taskStore.values().stream();
        if (query == null) {
            return stream;
        }
        if (StringUtils.isNotBlank(query.getTaskGroup())) {
            stream = stream.filter(d -> d.getTask().getTaskId().getGroup()
                    .equals(query.getTaskGroup()));
        }
        if (StringUtils.isNotBlank(query.getTaskName())) {
            stream = stream.filter(
                    d -> d.getTask().getTaskId().getName().contains(query.getTaskName()));
        }
        if (StringUtils.isNotBlank(query.getTaskClass())) {
            stream = stream.filter(
                    d -> TaskReflectionUtils.taskClassNameOf(d.getTask()).contains(query.getTaskClass()));
        }
        if (!query.getStatuses().isEmpty()) {
            stream = stream.filter(d -> query.getStatuses().contains(d.getTaskStatus()));
        }
        return stream;
    }

    @Override
    public List<LocalDateTime> findNextFiredDateTimes(TaskId taskId, LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        InMemoryTaskDetail taskDetail = taskStore.get(taskId);
        if (taskDetail == null || taskDetail.isUnavailable()) {
            return Collections.emptyList();
        }
        CronExpression cronExpression = taskDetail.getTask().getCronExpression();
        return cronExpression.list(startDateTime, endDateTime);
    }

    @Override
    public List<TaskId> findUpcomingTasksBetween(LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        return taskStore.values().stream().filter(d -> !d.isUnavailable()).filter(d -> {
            LocalDateTime next = d.getNextFiredDateTime();
            return next != null && !next.isBefore(startDateTime) && next.isBefore(endDateTime);
        }).map(d -> d.getTask().getTaskId()).collect(Collectors.toList());
    }

    @Override
    public LocalDateTime computeNextFiredDateTime(TaskId taskId,
            LocalDateTime previousFiredDateTime) {
        InMemoryTaskDetail taskDetail = taskStore.get(taskId);
        if (taskDetail == null) {
            return null;
        }
        Task task = taskDetail.getTask();
        LocalDateTime nextFiredDateTime =
                task.getCronExpression().getNextFiredDateTime(previousFiredDateTime);
        // A repeat cap or a deadline turns the next occurrence into "none", which finishes the task.
        nextFiredDateTime = Task.capNextFiredDateTime(nextFiredDateTime, taskDetail.getRunCount(),
                task.getRepeatCount(), task.getStopAt());
        taskDetail.setFiredDateTimes(previousFiredDateTime, nextFiredDateTime);
        return nextFiredDateTime;
    }

    @Override
    public void restoreTasks(TaskRestoreHandler restoreHandler) {
        if (restoreHandler == null) {
            return;
        }
        for (InMemoryTaskDetail taskDetail : taskStore.values()) {
            if (taskDetail.isUnavailable()) {
                continue;
            }
            restoreHandler.onRestore(taskDetail.getTask().getTaskId(),
                    taskDetail.getNextFiredDateTime());
        }
    }

    @Override
    public boolean setTaskStatus(TaskId taskId, TaskStatus status) {
        InMemoryTaskDetail taskDetail = taskStore.get(taskId);
        return taskDetail != null && taskDetail.setTaskStatus(status);
    }

    @Override
    public boolean compareAndSetTaskStatus(TaskId taskId, TaskStatus expected, TaskStatus target) {
        InMemoryTaskDetail taskDetail = taskStore.get(taskId);
        return taskDetail != null && taskDetail.compareAndSetTaskStatus(expected, target);
    }

    @Override
    public void recordExecution(TaskExecutionLog executionLog) {
        if (executionLog == null) {
            return;
        }
        TaskId taskId = executionLog.getTaskId();
        Deque<TaskExecutionLog> logs =
                MapUtils.getOrCreate(executionLogs, taskId, ArrayDeque::new);
        synchronized (logs) {
            logs.addFirst(executionLog);
            while (logs.size() > logCapacity) {
                logs.removeLast();
            }
        }
        InMemoryTaskDetail taskDetail = taskStore.get(taskId);
        if (taskDetail != null) {
            taskDetail.recordRun(executionLog.isSuccess());
        }
    }

    @Override
    public List<TaskExecutionLog> findExecutionLogs(TaskId taskId, int limit, int offset) {
        Deque<TaskExecutionLog> logs = executionLogs.get(taskId);
        if (logs == null) {
            return Collections.emptyList();
        }
        List<TaskExecutionLog> snapshot;
        synchronized (logs) {
            snapshot = new ArrayList<>(logs);
        }
        Stream<TaskExecutionLog> stream = snapshot.stream().skip(Math.max(0, offset));
        if (limit > 0) {
            stream = stream.limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    @Override
    public void recordMisfire(TaskId taskId, LocalDateTime missedDateTime) {
        InMemoryTaskDetail taskDetail = taskStore.get(taskId);
        if (taskDetail != null) {
            taskDetail.recordMisfire();
        }
    }

}
