package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;

/**
 * 
 * Callbacks for watching a task move through its lifecycle.
 * 
 * <p>
 * Listeners are invoked on scheduler and worker threads, and a slow listener delays the work it is
 * observing. They must be quick and must not throw; a throwing listener is reported to the error
 * handler and the remaining listeners still run.
 * 
 * @Description: TaskListener
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public interface TaskListener {

    /** The task has been parked in the timing wheel with a known next fire time. */
    default void onTaskScheduled(LocalDateTime scheduledDateTime, TaskDetail taskDetail) {}

    /** Its fire time has come round and it has been handed to a worker. */
    default void onTaskTriggered(LocalDateTime firedDateTime, TaskDetail taskDetail) {}

    /** Its body is about to run. */
    default void onTaskBegan(LocalDateTime firedDateTime, TaskDetail taskDetail) {}

    /** Its body has returned or thrown; exactly one of the last two arguments is set. */
    default void onTaskEnded(LocalDateTime firedDateTime, TaskDetail taskDetail, Object returnValue,
            Throwable e) {}

    /** A fire time was reached too late and the misfire policy was applied. */
    default void onTaskMisfired(LocalDateTime missedDateTime, TaskDetail taskDetail) {}

    /** The task has been withdrawn. */
    default void onTaskCanceled(TaskDetail taskDetail) {}

    /** The cron expression produced no further fire time. */
    default void onTaskFinished(TaskDetail taskDetail) {}

}
