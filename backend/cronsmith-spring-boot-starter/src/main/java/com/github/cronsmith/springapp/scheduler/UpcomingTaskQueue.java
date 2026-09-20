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
package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 
 * Holds the tasks waiting for their fire time, and hands them back when the clock reaches it.
 * 
 * <p>
 * The queue is driven, not self-driving: something outside calls {@link #advance(LocalDateTime)} on
 * a regular tick and acts on what comes back. That keeps the single-node implementation (an
 * in-memory timing wheel) and a future clustered one (a query against shared storage) behind the
 * same interface.
 * 
 * @Description: UpcomingTaskQueue
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public interface UpcomingTaskQueue {

    /**
     * Parks a task until the given time. A task already waiting is re-parked at the new time rather
     * than queued twice.
     * 
     * @return false if that time has already passed, in which case nothing was queued and the
     *         caller has to decide what to do about the missed fire time
     */
    boolean offer(LocalDateTime firedDateTime, TaskId taskId);

    /**
     * Moves the clock to the given time and returns everything that came due on the way, including
     * fire times from ticks that were skipped entirely.
     */
    Collection<TaskId> advance(LocalDateTime now);

    /**
     * Withdraws a task that has not fired yet.
     * 
     * @return whether it was waiting
     */
    boolean remove(TaskId taskId);

    /**
     * Whether the task is waiting for a fire time.
     */
    boolean contains(TaskId taskId);

    /**
     * How many tasks are waiting.
     */
    int size();

    /**
     * Discards everything waiting.
     */
    void clear();

}
