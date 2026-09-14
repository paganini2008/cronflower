package com.github.cronflow.springapp.server.jpa;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One DAG run, stored in {@code cf_dag_log} — the DAG counterpart of cronsmith's {@code cs_task_log}.
 * Carries the run's parameter (initial state) and return value (final state), so a run can be
 * inspected end to end; the per-node input/output lives in {@code cf_dag_node_log}, joined on
 * {@code run_id}.
 *
 * @Description: DagLogEntity
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "cf_dag_log")
public class DagLogEntity {

    @Id
    @Column(name = "run_id", length = 64)
    private String runId;

    /** The parent run's id when this run is a subgraph; null for a top-level run. */
    @Column(name = "parent_run_id", length = 64)
    private String parentRunId;

    @Column(name = "application", length = 255)
    private String application;

    @Column(name = "graph_name", length = 255)
    private String graph;

    /** What triggered the run, e.g. the cronsmith task {@code group/name}. */
    @Column(name = "triggered_by", length = 512)
    private String triggeredBy;

    /** RUNNING / SUCCESS / FAILED. */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Column(name = "node_count")
    private Integer nodeCount;

    @Column(name = "failed_node", length = 255)
    private String failedNode;

    /** The run's parameter: the initial state (the triggering task's return value). JSON. */
    @Column(name = "input_parameter", length = 1_000_000)
    private String inputParameter;

    /** The run's return value: the final state. JSON. */
    @Column(name = "return_value", length = 1_000_000)
    private String returnValue;

    @Column(name = "error_detail", length = 1_000_000)
    private String errorDetail;
}
