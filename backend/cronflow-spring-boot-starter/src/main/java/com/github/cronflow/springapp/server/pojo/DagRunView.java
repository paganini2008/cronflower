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
