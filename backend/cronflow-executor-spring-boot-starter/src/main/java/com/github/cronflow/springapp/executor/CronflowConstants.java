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

/**
 * Executor-side constants for cronflower. Everything lives under one HTTP namespace ({@link #API_BASE}).
 * The two {@code *_SERVER_API_PREFIX} values are the defaults for the prefixes this executor calls the
 * server with, and must match the server's {@code cronflow.scheduler.api-prefix} /
 * {@code cronflow.server.api-prefix}. {@link #RUN_PATH} and {@link #PING_PATH} are this executor's own
 * inbound endpoints that the scheduler calls back on.
 */
public final class CronflowConstants {

    private CronflowConstants() {}

    /** Root namespace for every cronflower HTTP API. */
    public static final String API_BASE = "/cronflow";

    /** Default prefix this executor calls the scheduler (task) API with: {@code /cronflow/scheduler}. */
    public static final String SCHEDULER_API_PREFIX = API_BASE + "/scheduler";

    /** Default prefix this executor calls the DAG API with: {@code /cronflow/dag}. */
    public static final String DAG_API_PREFIX = API_BASE + "/dag";

    /** This executor's own inbound endpoint namespace: {@code /cronflow/executor}. */
    public static final String EXECUTOR_BASE = API_BASE + "/executor";

    /** This executor's inbound run endpoint the scheduler dispatches to: {@code /cronflow/executor/run}. */
    public static final String RUN_PATH = EXECUTOR_BASE + "/run";

    /** This executor's inbound liveness probe the scheduler calls: {@code /cronflow/executor/ping}. */
    public static final String PING_PATH = EXECUTOR_BASE + "/ping";
}
