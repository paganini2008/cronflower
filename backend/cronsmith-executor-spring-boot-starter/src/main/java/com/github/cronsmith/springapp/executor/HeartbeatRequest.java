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
 * A lightweight liveness ping sent on an interval after the initial registration. It carries no task
 * list — tasks are reconciled only at startup — and only keeps this executor present, and reachable,
 * in the server's in-memory executor list (e.g. after a server restart or leader change).
 *
 * @Description: HeartbeatRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record HeartbeatRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, Integer weight) {
}
