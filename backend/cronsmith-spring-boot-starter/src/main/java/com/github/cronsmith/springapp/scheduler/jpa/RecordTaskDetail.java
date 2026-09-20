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
package com.github.cronsmith.springapp.scheduler.jpa;

import java.time.LocalDateTime;
import java.util.Map;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskReflectionUtils;
import com.github.cronsmith.springapp.scheduler.TaskStatus;

/**
 * A {@link TaskDetail} backed by a row read into a map, keyed the way
 * {@code AbstractTask} reads a stored task. The runnable task is rebuilt through the task factory,
 * which on the server yields an executor-dispatching task (or a direct HTTP-API task).
 *
 * @Description: RecordTaskDetail
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class RecordTaskDetail implements TaskDetail {

    private static final long serialVersionUID = -9138745562018394720L;

    private final Map<String, Object> record;

    public RecordTaskDetail(Map<String, Object> record) {
        this.record = record;
    }

    @Override
    public Task getTask() {
        return TaskReflectionUtils.getTaskObject((String) record.get("taskClass"), record);
    }

    @Override
    public TaskId getTaskId() {
        return TaskId.of((String) record.get("taskGroup"), (String) record.get("taskName"));
    }

    @Override
    public String getInitialParameter() {
        return (String) record.get("initialParameter");
    }

    @Override
    public TaskStatus getTaskStatus() {
        return TaskStatus.forName((String) record.get("taskStatus"));
    }

    @Override
    public LocalDateTime getNextFiredDateTime() {
        return (LocalDateTime) record.get("next_fired_datetime");
    }

    @Override
    public LocalDateTime getPreviousFiredDateTime() {
        return (LocalDateTime) record.get("prev_fired_datetime");
    }

    @Override
    public LocalDateTime getLastModified() {
        return (LocalDateTime) record.get("last_modified");
    }

    @Override
    public long getRunCount() {
        return longOf(record.get("runCount"));
    }

    @Override
    public long getFailureCount() {
        return longOf(record.get("failureCount"));
    }

    @Override
    public long getMisfireCount() {
        return longOf(record.get("misfireCount"));
    }

    private static long longOf(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    @Override
    public String toString() {
        return "Task Id: " + getTaskId() + ", Task Status: " + getTaskStatus() + ", Next Fired: "
                + getNextFiredDateTime();
    }

}
