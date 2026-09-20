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
 * A task definition discovered from a {@link Task}-annotated method, sent to the server so it can be
 * scheduled. The server is the source of truth; this is only what the executor knows about itself.
 *
 * @Description: TaskMetadata
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record TaskMetadata(String taskGroup, String taskName, String className, String beanName,
        String methodName, String cron, String parser, String description, String initialParameter,
        long timeout, int maxRetryCount, long retryInterval, String misfirePolicy, int repeatCount,
        String stopAt) {
}
