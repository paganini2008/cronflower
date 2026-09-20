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
 * The result the executor reports back after a run finishes. The server writes it to the execution
 * log and decides, on its own, whether to retry. Times are epoch milliseconds. {@code executorRepr}
 * tags the attempt with this executor ({@code applicationName,instanceId,host:port}).
 *
 * @Description: CompleteRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record CompleteRequest(String executionId, String taskGroup, String taskName, boolean success,
        String returnValue, String errorDetail, long firedAt, long completedAt, long elapsed,
        int attempt, String executorRepr) {
}
