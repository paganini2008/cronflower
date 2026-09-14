package com.github.cronflow.springapp.server.jpa;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One node execution within a DAG run, stored in {@code cf_dag_node_log}. Query by {@code run_id} to
 * get every node's input (the state it received) and output (the channels it returned), in order.
 *
 * @Description: DagNodeLogEntity
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "cf_dag_node_log")
public class DagNodeLogEntity {

    /** {@code runId + "#" + seq}. */
    @Id
    @Column(name = "id", length = 96)
    private String id;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "graph_name", length = 255)
    private String graph;

    @Column(name = "node_name", length = 255)
    private String node;

    @Column(name = "seq")
    private Integer seq;

    /** SUCCESS / FAILED / SKIPPED / SUBGRAPH. */
    @Column(name = "status", length = 16)
    private String status;

    /** The node's input: the run state it received. JSON. */
    @Column(name = "input_param", length = 1_000_000)
    private String inputParam;

    /** The node's output: the channels it changed. JSON. */
    @Column(name = "output", length = 1_000_000)
    private String output;

    /** Which executor ran it (its node-run URL), or the subgraph name for a subgraph node. */
    @Column(name = "executor", length = 255)
    private String executor;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Column(name = "error_detail", length = 1_000_000)
    private String errorDetail;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    public static String idOf(String runId, int seq) {
        return com.github.cronflow.springapp.server.DagIds.nodeLogId(runId, seq);
    }
}
