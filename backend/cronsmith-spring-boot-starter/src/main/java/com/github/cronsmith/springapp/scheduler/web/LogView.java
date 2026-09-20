/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
