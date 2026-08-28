package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 *
 * A task together with the scheduling state the task manager holds for it. Instances are snapshots:
 * reading one twice may yield different values, and callers must not assume the state is still
 * current by the time they act on it.
 *
 * <p>
 * Serializable so a snapshot can be returned across cluster nodes.
 *
 * @Description: TaskDetail
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public interface TaskDetail extends Serializable {

    Task getTask();

    default TaskId getTaskId() {
        return getTask().getTaskId();
    }

    String getInitialParameter();

    TaskStatus getTaskStatus();

    LocalDateTime getNextFiredDateTime();

    LocalDateTime getPreviousFiredDateTime();

    LocalDateTime getLastModified();

    /**
     * How many times this task has been run, successfully or not.
     */
    default long getRunCount() {
        return 0L;
    }

    /**
     * How many of those runs ended in a failure.
     */
    default long getFailureCount() {
        return 0L;
    }

    /**
     * How many fire times were missed and handled by the misfire policy.
     */
    default long getMisfireCount() {
        return 0L;
    }

    /**
     * Whether the scheduler should skip this task when its fire time arrives.
     */
    default boolean isUnavailable() {
        TaskStatus status = getTaskStatus();
        return status == null || status.isUnavailable();
    }

}
