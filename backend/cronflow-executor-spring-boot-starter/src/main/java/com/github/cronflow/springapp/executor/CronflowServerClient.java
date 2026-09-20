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
package com.github.cronflow.springapp.executor;
import com.github.cronflow.springapp.executor.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.executor.pojo.DagRegistrationRequest;

/**
 * The executor's view of the cronflow server: register DAG definitions + trigger bindings, and
 * heartbeat. Mirrors {@code CronsmithServerClient}; the implementation fails over across the
 * configured server URLs.
 *
 * @Description: CronflowServerClient
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface CronflowServerClient {

    String REGISTER_SUBPATH = "/dags/register";
    String HEARTBEAT_SUBPATH = "/dags/heartbeat";

    /** Register this executor and its DAGs; returns the assigned instance id, or null on failure. */
    String register(DagRegistrationRequest request);

    /** Send a heartbeat; returns whether a server accepted it. */
    boolean heartbeat(DagHeartbeatRequest request);

}
