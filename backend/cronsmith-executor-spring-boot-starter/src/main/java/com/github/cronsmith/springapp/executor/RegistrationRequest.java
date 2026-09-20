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

import java.util.List;

/**
 * What an executor sends to the server on startup and on every heartbeat. The URLs are fully
 * resolved — including any context path, servlet path, WebFlux base path or separate management port
 * — so the server can call them back verbatim without knowing anything about this executor's setup.
 *
 * @Description: RegistrationRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record RegistrationRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, List<TaskMetadata> tasks, Integer weight) {
}
