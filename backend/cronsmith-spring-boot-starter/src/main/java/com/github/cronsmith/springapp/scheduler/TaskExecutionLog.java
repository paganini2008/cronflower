package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.github.cronsmith.utils.ExceptionUtils;

/**
 *
 * A record of one attempt to run a task: when it was due, when it actually started, how long it
 * took, and what came back. One row is written per attempt, so a task that failed twice before
 * succeeding leaves three.
 *
 * <p>
 * Serializable so a log entry can be replicated across cluster nodes.
 *
 * @Description: TaskExecutionLog
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TaskExecutionLog implements Serializable {

    private static final long serialVersionUID = 3486547908234561239L;

    /** How much of a return value or stack trace is kept, to bound the size of a log row. */
    public static final int MAX_TEXT_LENGTH = 4000;

    private final TaskId taskId;
    private final LocalDateTime scheduledDateTime;
    private LocalDateTime firedDateTime;
    private LocalDateTime completedDateTime;
    private String returnValue;
    private String errorDetail;
    private long elapsed;
    private int attempt;
    private boolean success;
    private String parameter;
    private String schedulerRepr;
    private String executorRepr;

    public TaskExecutionLog(TaskId taskId, LocalDateTime scheduledDateTime) {
        this.taskId = taskId;
        this.scheduledDateTime = scheduledDateTime;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    /**
     * When the task was due to run.
     */
    public LocalDateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    /**
     * When it actually started. The gap from the scheduled time is the scheduling delay.
     */
    public LocalDateTime getFiredDateTime() {
        return firedDateTime;
    }

    public TaskExecutionLog firedAt(LocalDateTime firedDateTime) {
        this.firedDateTime = firedDateTime;
        return this;
    }

    public LocalDateTime getCompletedDateTime() {
        return completedDateTime;
    }

    public TaskExecutionLog completedAt(LocalDateTime completedDateTime) {
        this.completedDateTime = completedDateTime;
        return this;
    }

    public String getReturnValue() {
        return returnValue;
    }

    /**
     * Stores the string form of what the task returned, truncated to {@link #MAX_TEXT_LENGTH}.
     */
    public TaskExecutionLog returnValue(Object returnValue) {
        this.returnValue = truncate(returnValue != null ? returnValue.toString() : null);
        return this;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    /**
     * Stores the stack trace of the failure, truncated to {@link #MAX_TEXT_LENGTH}, and marks this
     * attempt as unsuccessful.
     */
    public TaskExecutionLog error(Throwable e) {
        this.errorDetail = truncate(ExceptionUtils.toString(e));
        this.success = false;
        return this;
    }

    public long getElapsed() {
        return elapsed;
    }

    public TaskExecutionLog elapsed(long elapsed) {
        this.elapsed = elapsed;
        return this;
    }

    /**
     * Which attempt this was: 0 for the first run, 1 for the first retry, and so on.
     */
    public int getAttempt() {
        return attempt;
    }

    public TaskExecutionLog attempt(int attempt) {
        this.attempt = attempt;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public TaskExecutionLog success(boolean success) {
        this.success = success;
        return this;
    }

    public String getParameter() {
        return parameter;
    }

    /** Records the input parameter the task was invoked with for this run. */
    public TaskExecutionLog parameter(String parameter) {
        this.parameter = truncate(parameter);
        return this;
    }

    public String getSchedulerRepr() {
        return schedulerRepr;
    }

    /** The scheduler node that dispatched this run, as {@code applicationName,instanceId,host:port}. */
    public TaskExecutionLog schedulerRepr(String schedulerRepr) {
        this.schedulerRepr = schedulerRepr;
        return this;
    }

    public String getExecutorRepr() {
        return executorRepr;
    }

    /** The executor that ran this attempt, as {@code applicationName,instanceId,host:port}. */
    public TaskExecutionLog executorRepr(String executorRepr) {
        this.executorRepr = executorRepr;
        return this;
    }

    /**
     * Restores the stored text as it came out of the database, without re-truncating it. Used when
     * a log row is read back; callers recording a new run go through {@link #returnValue(Object)}.
     */
    public TaskExecutionLog setStoredReturnValue(String returnValue) {
        this.returnValue = returnValue;
        return this;
    }

    /**
     * Restores the stored stack trace, without touching the success flag: whether the attempt
     * succeeded was read from its own column.
     */
    public TaskExecutionLog setStoredErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
        return this;
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }

    @Override
    public String toString() {
        return "TaskExecutionLog [taskId=" + taskId + ", scheduled=" + scheduledDateTime
                + ", attempt=" + attempt + ", success=" + success + ", elapsed=" + elapsed + "ms]";
    }

}
