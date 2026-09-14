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
