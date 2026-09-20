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
 * What the server sends to run a task once. {@code executionId} correlates this dispatch with the
 * {@link CompleteRequest} the executor sends back.
 *
 * @Description: RunRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record RunRequest(String executionId, String taskGroup, String taskName, String className,
        String beanName, String methodName, String initialParameter, int attempt, long timeout,
        String schedulerRepr) {
}
