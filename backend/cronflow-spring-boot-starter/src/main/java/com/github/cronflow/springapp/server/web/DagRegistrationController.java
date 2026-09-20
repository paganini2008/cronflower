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
package com.github.cronflow.springapp.server.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.server.ClusterDagRegistry;
import com.github.cronflow.springapp.server.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.server.pojo.DagHeartbeatResponse;
import com.github.cronflow.springapp.server.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.server.pojo.DagRegistrationResponse;

/**
 * Receives executor registrations and heartbeats. In its own {@code .web} package so the cronflow
 * API prefix ({@code cronflow.server.api-prefix}) applies here without touching cronsmith's paths.
 *
 * @Description: DagRegistrationController
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@RestController
public class DagRegistrationController {

    public static final String REGISTER_PATH = "/dags/register";
    public static final String HEARTBEAT_PATH = "/dags/heartbeat";

    private final ClusterDagRegistry registry;

    public DagRegistrationController(ClusterDagRegistry registry) {
        this.registry = registry;
    }

    @PostMapping(REGISTER_PATH)
    public DagRegistrationResponse register(@RequestBody DagRegistrationRequest request) {
        return new DagRegistrationResponse(registry.register(request));
    }

    @PostMapping(HEARTBEAT_PATH)
    public DagHeartbeatResponse heartbeat(@RequestBody DagHeartbeatRequest request) {
        return new DagHeartbeatResponse(registry.heartbeat(request));
    }

}
