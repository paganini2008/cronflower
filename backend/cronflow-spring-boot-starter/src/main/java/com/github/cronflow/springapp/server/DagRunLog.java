package com.github.cronflow.springapp.server;

import java.time.LocalDateTime;
import java.util.List;
import com.github.cronflow.springapp.server.pojo.DagNodeView;
import com.github.cronflow.springapp.server.pojo.DagRunView;

/**
 * Persists DAG run history for traceability: the run itself ({@code cf_dag_log}, with parameter and
 * return value) and each node execution ({@code cf_dag_node_log}, with per-node input and output),
 * joinable by {@code runId}. The DAG counterpart of cronsmith's {@code cs_task_log}.
 *
 * <p>
 * Also the read side for the console: runs are listed and drilled into ({@link DagRunView} +
 * {@link DagNodeView}). Reads return JPA-free views so the jOOQ path never touches {@code jpa.*}.
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

    // ---- read side (console) -------------------------------------------------------------------

    /** Recent runs, newest first, filtered by any of {@code application}/{@code graph}/{@code status}
     *  (null or blank = no filter), paged by {@code limit}/{@code offset}. */
    List<DagRunView> listRuns(String application, String graph, String status, int limit, int offset);

    /** Total runs matching the same filters (for pagination). */
    int countRuns(String application, String graph, String status);

    /** One run by id, or {@code null} if unknown. */
    DagRunView findRun(String runId);

    /** The node executions of a run, in {@code seq} order. */
    List<DagNodeView> nodesOf(String runId);

    /** Child (subgraph) runs whose {@code parentRunId} is {@code runId}, newest first. */
    List<DagRunView> childRuns(String parentRunId);

}
