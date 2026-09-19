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
