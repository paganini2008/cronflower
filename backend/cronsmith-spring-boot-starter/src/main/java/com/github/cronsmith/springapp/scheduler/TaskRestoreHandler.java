package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;

/**
 * 
 * Receives the tasks a task manager finds still outstanding at start-up, so the scheduler can put
 * them back into the timing wheel.
 * 
 * @Description: TaskRestoreHandler
 * @Author: Fred Feng
 * @Date: 30/04/2025
 * @Version 1.0.0
 */
@FunctionalInterface
public interface TaskRestoreHandler {

    /**
     * @param taskId the task to restore
     * @param nextFiredDateTime its stored next fire time, which may already be in the past
     */
    void onRestore(TaskId taskId, LocalDateTime nextFiredDateTime);

}
