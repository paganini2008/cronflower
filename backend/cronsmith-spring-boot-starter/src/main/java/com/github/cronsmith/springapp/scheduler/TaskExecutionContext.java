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

import java.util.Optional;

/**
 * Exposes the log row of the run currently executing on this thread, so a task body can enrich its own
 * execution log (e.g. a remote-dispatch task recording which scheduler dispatched it and which executor
 * ran it) without changing the {@link Task#execute} signature.
 *
 * <p>
 * {@link TaskInvoker} sets the current log around the body and clears it afterwards. The body always
 * runs on the same thread the context was set on — the timeout path sets it on the worker thread it
 * submits the body to — so there is no cross-thread leakage, and concurrent runs each see their own log.
 *
 * @Description: TaskExecutionContext
 * @Author: Fred Feng
 * @Date: 28/08/2026
 * @Version 1.0.0
 */
public final class TaskExecutionContext {

    private static final ThreadLocal<TaskExecutionLog> CURRENT = new ThreadLocal<>();

    private TaskExecutionContext() {}

    static void set(TaskExecutionLog log) {
        CURRENT.set(log);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** The execution log of the run in progress on this thread, if a task body is running. */
    public static Optional<TaskExecutionLog> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
