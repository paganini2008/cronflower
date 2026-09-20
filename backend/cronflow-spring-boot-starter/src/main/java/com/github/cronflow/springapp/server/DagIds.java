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
package com.github.cronflow.springapp.server;

/**
 * Row-id composition for the cronflow tables. Deliberately JPA-free so the jOOQ store/run-log never
 * depends on the JPA entities (a jOOQ-only deployment must not need jakarta.persistence).
 *
 * @Description: DagIds
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public final class DagIds {

    private DagIds() {}

    /** cf_task_dag id: {@code application + "/" + graph}. */
    public static String taskDagId(String application, String graph) {
        return application + "/" + graph;
    }

    /** cf_dag_node_log id: {@code runId + "#" + seq}. */
    public static String nodeLogId(String runId, int seq) {
        return runId + "#" + seq;
    }

}
