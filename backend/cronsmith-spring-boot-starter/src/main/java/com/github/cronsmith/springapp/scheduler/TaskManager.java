package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 
 * Where tasks and their scheduling state live. The scheduler holds no durable state of its own, so
 * this is the single source of truth for what exists, what state it is in, and when it next runs.
 * 
 * <p>
 * Implementations must be safe to call from several threads at once. In particular
 * {@link #compareAndSetTaskStatus} has to be atomic, since it is what keeps the scheduler thread
 * and the worker threads from overwriting each other's view of a task's lifecycle.
 * 
 * @Description: TaskManager
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public interface TaskManager {

    /**
     * Stores a task, or updates it in place if one with the same id already exists. An existing
     * task keeps its status and fire times; only its definition is refreshed.
     * 
     * @param initialParameter overrides {@link Task#getInitialParameter()} when not blank
     */
    TaskDetail saveTask(Task task, String initialParameter) throws TaskException;

    /**
     * Deletes a task and returns what was stored for it, or null if there was nothing.
     */
    TaskDetail removeTask(TaskId taskId) throws TaskException;

    /**
     * @param thrown whether a missing task is an error rather than a null result
     */
    TaskDetail getTaskDetail(TaskId taskId, boolean thrown) throws TaskException;

    boolean hasTask(TaskId taskId) throws TaskException;

    default String getInitialParameter(TaskId taskId) throws TaskException {
        TaskDetail taskDetail = getTaskDetail(taskId, false);
        return taskDetail != null ? taskDetail.getInitialParameter() : null;
    }

    default TaskStatus getTaskStatus(TaskId taskId) throws TaskException {
        TaskDetail taskDetail = getTaskDetail(taskId, false);
        return taskDetail != null ? taskDetail.getTaskStatus() : null;
    }

    int getTaskCount(TaskQuery query) throws TaskException;

    List<TaskDetail> findTaskDetails(TaskQuery query) throws TaskException;

    /**
     * The fire times this task's cron expression produces within the given window. Used to preview
     * a schedule; it does not change any stored state.
     */
    List<LocalDateTime> findNextFiredDateTimes(TaskId taskId, LocalDateTime startDateTime,
            LocalDateTime endDateTime) throws TaskException;

    /**
     * The tasks whose stored next fire time falls in the given window. Not used by the single-node
     * scheduler, which keeps its own timing wheel, but it is the hook a clustered scheduler uses to
     * claim work from shared storage.
     */
    List<TaskId> findUpcomingTasksBetween(LocalDateTime startDateTime, LocalDateTime endDateTime)
            throws TaskException;

    /**
     * Advances the task to its next fire time after the given point and stores the result.
     * 
     * @return the new next fire time, or null when the cron expression is exhausted
     */
    LocalDateTime computeNextFiredDateTime(TaskId taskId, LocalDateTime previousFiredDateTime)
            throws TaskException;

    /**
     * Hands every task that was left outstanding to the handler, so a restarted scheduler can pick
     * up where it stopped. Tasks in a terminal or paused state are skipped.
     */
    void restoreTasks(TaskRestoreHandler restoreHandler) throws TaskException;

    /**
     * Moves the task to the given status if {@link TaskStatus#canTransitionTo} allows it.
     * 
     * @return whether the status was written
     */
    boolean setTaskStatus(TaskId taskId, TaskStatus status) throws TaskException;

    /**
     * Moves the task to the given status only if it is currently in the expected one. This is the
     * atomic form callers need when two threads may both act on the same task.
     * 
     * @return whether the status was written
     */
    boolean compareAndSetTaskStatus(TaskId taskId, TaskStatus expected, TaskStatus target)
            throws TaskException;

    /**
     * Records one attempt to run a task, and updates that task's run, failure and misfire counters.
     */
    void recordExecution(TaskExecutionLog executionLog) throws TaskException;

    /**
     * The most recent attempts for a task, newest first.
     */
    List<TaskExecutionLog> findExecutionLogs(TaskId taskId, int limit, int offset)
            throws TaskException;

    /**
     * Notes that a fire time was missed, for reporting through
     * {@link TaskDetail#getMisfireCount()}.
     */
    void recordMisfire(TaskId taskId, LocalDateTime missedDateTime) throws TaskException;

    /**
     * Releases whatever resources the implementation holds. Called when the scheduler shuts down;
     * implementations that own nothing may do nothing.
     */
    default void close() throws TaskException {}

}
