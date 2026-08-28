package com.github.cronsmith.springapp.scheduler.web;

import java.time.LocalDateTime;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;

/**
 * A JSON-friendly view of one {@link TaskExecutionLog} row.
 *
 * @Description: LogView
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record LogView(String taskGroup, String taskName, LocalDateTime scheduledDateTime,
        LocalDateTime firedDateTime, LocalDateTime completedDateTime, String parameter,
        String returnValue, String errorDetail, long elapsed, int attempt, boolean success,
        String schedulerRepr, String executorRepr) {

    public static LogView of(TaskExecutionLog log) {
        TaskId id = log.getTaskId();
        return new LogView(id.getGroup(), id.getName(), log.getScheduledDateTime(),
                log.getFiredDateTime(), log.getCompletedDateTime(), log.getParameter(),
                log.getReturnValue(), log.getErrorDetail(), log.getElapsed(), log.getAttempt(),
                log.isSuccess(), log.getSchedulerRepr(), log.getExecutorRepr());
    }

}
