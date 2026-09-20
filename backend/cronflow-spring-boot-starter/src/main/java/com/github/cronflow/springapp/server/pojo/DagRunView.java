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
 * A runtime, JPA-free view of one DAG run row ({@code cf_dag_log}) — returned by {@link DagRunLog}
 * reads to the query controller. Deliberately independent of the JPA entity so the jOOQ read path
 * never touches {@code jpa.*}, mirroring how cronsmith's {@code TaskManager} returns interfaces.
 *
 * @Description: DagRunView
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagRunView(String runId, String parentRunId, String application, String graph,
        String triggeredBy, String status, LocalDateTime startedAt, LocalDateTime finishedAt,
        Long elapsedMs, Integer nodeCount, String failedNode, String inputParameter,
        String returnValue, String errorDetail) {
}
