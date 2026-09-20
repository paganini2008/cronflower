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

import java.util.List;

/**
 * What an executor sends on startup: who it is, where to reach it, and the tasks it can run. The URLs
 * are already fully resolved by the executor, so the leader calls them back verbatim.
 *
 * @Description: RegistrationRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record RegistrationRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, List<ExecutorTaskMetadata> tasks, Integer weight) {

    /** Routing weight for WEIGHTED dispatch; absent means the default of 1. */
    public int weightOrDefault() {
        return weight != null && weight > 0 ? weight : 1;
    }
}
