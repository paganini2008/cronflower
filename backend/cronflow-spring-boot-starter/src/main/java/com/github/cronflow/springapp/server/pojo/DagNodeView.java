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
package com.github.cronflow.springapp.server.pojo;

import java.time.LocalDateTime;

/**
 * A runtime, JPA-free view of one DAG node-execution row ({@code cf_dag_node_log}), joinable to a
 * {@link DagRunView} by {@code runId}. Carries the node's input, output and the executor that ran it.
 *
 * @Description: DagNodeView
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagNodeView(String runId, String graph, String node, Integer seq, String status,
        String inputParam, String output, String executor, Long elapsedMs, String errorDetail,
        LocalDateTime loggedAt) {
}
