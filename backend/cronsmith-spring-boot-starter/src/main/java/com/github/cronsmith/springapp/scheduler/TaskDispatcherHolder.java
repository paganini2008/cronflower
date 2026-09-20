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

import com.github.cronsmith.springapp.scheduler.TaskInvocationException;

/**
 * Holds the single {@link TaskDispatcher} for this process, the way the core holds a single custom
 * task factory. A rebuilt task reaches the dispatcher through here, so the task object itself carries
 * only data and stays serializable — which matters when the store is in-memory and hands the very
 * same object back, or when a task is replicated to another node.
 *
 * @Description: TaskDispatcherHolder
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public final class TaskDispatcherHolder {

    private static volatile TaskDispatcher dispatcher;

    private TaskDispatcherHolder() {}

    public static void set(TaskDispatcher taskDispatcher) {
        dispatcher = taskDispatcher;
    }

    public static TaskDispatcher require() {
        TaskDispatcher current = dispatcher;
        if (current == null) {
            throw new TaskInvocationException("No TaskDispatcher configured on this node");
        }
        return current;
    }

}
