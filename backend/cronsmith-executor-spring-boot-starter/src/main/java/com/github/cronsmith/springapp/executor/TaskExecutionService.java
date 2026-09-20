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
package com.github.cronsmith.springapp.executor;

/**
 * Runs a dispatched task and reports the outcome back to the server.
 *
 * <p>
 * An interface so the invocation strategy can be replaced (a fake in tests, a different threading or
 * security model in production). The default is {@link DefaultTaskExecutionService}.
 *
 * @Description: TaskExecutionService
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public interface TaskExecutionService {

    /**
     * Accept a dispatch. Implementations run it off the calling thread and, when it finishes, send a
     * {@link CompleteRequest} to the server. Retry, timeout and logging are the server's job.
     */
    void dispatch(RunRequest request);

}
