package com.github.cronflow.springapp.server;

import java.time.LocalDateTime;

/**
 * Persists DAG run history for traceability: the run itself ({@code cf_dag_log}, with parameter and
 * return value) and each node execution ({@code cf_dag_node_log}, with per-node input and output),
 * joinable by {@code runId}. The DAG counterpart of cronsmith's {@code cs_task_log}.
 *
 * @Description: DagRunLog
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface DagRunLog {

    /** Record the start of a run (status RUNNING), with its parameter (initial state, JSON).
     *  {@code parentRunId} links a subgraph run to its parent; null for a top-level run. */
    void begin(String runId, String parentRunId, String application, String graph, String triggeredBy,
            LocalDateTime startedAt, String inputParameter);

    /** Record the end of a run, with its status and return value (final state, JSON). */
    void finish(String runId, String status, LocalDateTime finishedAt, long elapsedMs, int nodeCount,
            String failedNode, String errorDetail, String returnValue);

    /** Record one node execution: its input (state received) and output (channels returned), JSON. */
    void node(String runId, String graph, String node, int seq, String status, String inputParam,
            String output, String executor, long elapsedMs, String errorDetail);

}
