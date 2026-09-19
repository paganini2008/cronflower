package com.github.cronflow.springapp.server.pojo;

import java.util.List;

/**
 * The full drill-down of one DAG run: the run itself, its node executions (in {@code seq} order), any
 * child (subgraph) runs it spawned, and — while it is still RUNNING — the nodes currently in flight
 * ({@code running}), which the console pulses on the graph. Everything reachable by a single
 * {@code runId}.
 *
 * @Description: DagRunDetail
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagRunDetail(DagRunView run, List<DagNodeView> nodes, List<DagRunView> children,
        List<String> running) {
}
