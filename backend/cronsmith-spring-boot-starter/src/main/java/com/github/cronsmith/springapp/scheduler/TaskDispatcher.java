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

/**
 * Runs one occurrence of a task on an executor and returns its result — synchronously, so the
 * cronsmith {@code TaskInvoker} can treat a remote run exactly like a local one and get retry,
 * timeout and logging for free.
 *
 * <p>
 * The transport underneath is asynchronous: {@link #dispatchAndWait} sends the run and blocks; the
 * executor's callback arrives via {@link #complete} and unblocks it.
 *
 * @Description: TaskDispatcher
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public interface TaskDispatcher {

    /**
     * Dispatch a run and block until the executor reports back. Returns the task's return value, or
     * throws if the executor reported a failure or never answered in time.
     */
    Object dispatchAndWait(DispatchRequest request);

    /** Deliver an executor's completion, unblocking the matching {@link #dispatchAndWait}. */
    void complete(CompleteRequest result);

}
