package com.github.cronflow.springapp.server.pojo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A replicated {@code DagRunLog} write, broadcast across the cluster so that in the node-local store
 * model (per-node H2/SQLite) every node keeps the full run history — exactly how cronsmith replicates
 * its task log and how {@link DagSyncMessage} replicates DAG definitions. One record covers all three
 * write ops; {@code op} says which, and the unused fields stay null.
 *
 * @Description: DagRunLogMessage
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagRunLogMessage(String op, String runId, String parentRunId, String application,
        String graph, String triggeredBy, LocalDateTime startedAt, String inputParameter,
        String status, LocalDateTime finishedAt, Long elapsedMs, Integer nodeCount, String failedNode,
        String errorDetail, String returnValue, String node, Integer seq, String inputParam,
        String output, String executor) implements Serializable {

    public static final String BEGIN = "begin";
    public static final String FINISH = "finish";
    public static final String NODE = "node";

    public static DagRunLogMessage begin(String runId, String parentRunId, String application,
            String graph, String triggeredBy, LocalDateTime startedAt, String inputParameter) {
        return new DagRunLogMessage(BEGIN, runId, parentRunId, application, graph, triggeredBy,
                startedAt, inputParameter, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    public static DagRunLogMessage finish(String runId, String status, LocalDateTime finishedAt,
            long elapsedMs, int nodeCount, String failedNode, String errorDetail, String returnValue) {
        return new DagRunLogMessage(FINISH, runId, null, null, null, null, null, null, status,
                finishedAt, elapsedMs, nodeCount, failedNode, errorDetail, returnValue, null, null,
                null, null, null);
    }

    public static DagRunLogMessage node(String runId, String graph, String node, int seq,
            String status, String inputParam, String output, String executor, long elapsedMs,
            String errorDetail) {
        return new DagRunLogMessage(NODE, runId, null, null, graph, null, null, null, status, null,
                elapsedMs, null, null, errorDetail, null, node, seq, inputParam, output, executor);
    }
}
