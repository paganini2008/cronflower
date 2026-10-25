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
package com.github.cronflow.springapp;

import com.github.cronflow.springapp.scheduler.*;
import com.github.cronflow.springapp.dag.*;
import com.github.cronflow.springapp.web.*;
import com.github.cronflow.springapp.jpa.*;
import com.github.cronflow.springapp.jooq.*;
import com.github.cronflow.springapp.pojo.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Configuration for the cronflow server side.
 *
 * @Description: CronflowServerProperties
 * @Author: Fred Feng
 * @Date: 14/09/2026
 * @Version 1.0.0
 */
@Data
@ConfigurationProperties("cronflow.server")
public class CronflowServerProperties {

    /** Master switch. Off means cronflow does not load at all — a scheduler-only server is untouched. */
    private boolean enabled = true;

    /**
     * REST API prefix for the cronflow DAG endpoints (registration, authoring, runs). Must match the
     * executor's {@code cronflow.client.server-api-prefix}. Default {@code /cronflow/dag}; blank or
     * {@code /} serves at the root. Scoped to the DAG controllers only, so it never affects the
     * scheduler's paths (which live under {@code /cronflow/scheduler}).
     */
    private String apiPrefix = CronflowConstants.DAG_API_PREFIX;

    /** Format for storing DAG definitions in {@code cf_task_dag}: {@code json} (default) or {@code yaml}. */
    private String definitionFormat = "json";

    /** Default per-node HTTP dispatch timeout, in milliseconds, when a node declares none. */
    private long defaultNodeTimeoutMillis = 30000L;

    /** How long an executor instance is considered live after its last heartbeat, in seconds. */
    private long executorTtlSeconds = 60L;

}
