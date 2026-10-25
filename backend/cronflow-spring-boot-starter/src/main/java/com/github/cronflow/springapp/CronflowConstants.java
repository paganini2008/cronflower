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

/**
 * Server-side constants for cronflower. Everything lives under one HTTP namespace ({@link #API_BASE}),
 * split into a scheduler (task) area and a DAG (workflow) area so the two can be told apart by path for
 * routing, security and the Swagger UI. The executor side mirrors these in its own
 * {@code com.github.cronflow.springapp.executor.CronflowConstants}.
 */
public final class CronflowConstants {

    private CronflowConstants() {}

    /** Root namespace for every cronflower HTTP API. */
    public static final String API_BASE = "/cronflow";

    /** Scheduler (task) REST API prefix: {@code /cronflow/scheduler}. */
    public static final String SCHEDULER_API_PREFIX = API_BASE + "/scheduler";

    /** DAG (workflow) REST API prefix: {@code /cronflow/dag}. */
    public static final String DAG_API_PREFIX = API_BASE + "/dag";

    /** springdoc group id for the scheduler endpoints in the Swagger UI dropdown. */
    public static final String SWAGGER_GROUP_SCHEDULER = "scheduler";

    /** springdoc group id for the DAG endpoints in the Swagger UI dropdown. */
    public static final String SWAGGER_GROUP_DAG = "dag";
}
