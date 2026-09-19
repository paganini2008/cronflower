package com.github.cronflow.springapp.server.web;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.server.DagExecutorRegistry;
import com.github.cronflow.springapp.server.DagRunLog;
import com.github.cronflow.springapp.server.EngineDagRunner;
import com.github.cronflow.springapp.server.pojo.DagGraphView;
import com.github.cronflow.springapp.server.pojo.DagRunDetail;
import com.github.cronflow.springapp.server.pojo.DagRunPage;
import com.github.cronflow.springapp.server.pojo.DagRunView;

/**
 * Read-only query API for the console: registered DAG definitions ({@code cf_task_dag}) and run
 * history ({@code cf_dag_log} + {@code cf_dag_node_log}). In its own {@code .web} package so the
 * cronflow API prefix applies, exactly like cronsmith's task/log controllers under {@code /cronsmith}.
 *
 * @Description: DagQueryController
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@RestController
public class DagQueryController {

    private final DagExecutorRegistry registry;
    private final DagRunLog runLog;
    private final org.springframework.beans.factory.ObjectProvider<EngineDagRunner> coordinator;

    public DagQueryController(DagExecutorRegistry registry, DagRunLog runLog,
            org.springframework.beans.factory.ObjectProvider<EngineDagRunner> coordinator) {
        this.registry = registry;
        this.runLog = runLog;
        this.coordinator = coordinator;
    }

    /** Every registered graph with its definition (for the DAG list and diagram). */
    @GetMapping("/dags")
    public List<DagGraphView> dags() {
        return registry.graphs().stream().map(DagGraphView::of).toList();
    }

    /** One graph's definition, or 404 when unknown. */
    @GetMapping("/dags/{application}/{graph}")
    public ResponseEntity<DagGraphView> dag(@PathVariable String application,
            @PathVariable String graph) {
        return registry.graphs().stream()
                .filter(g -> g.definition().graph().equals(graph)
                        && g.application().equals(application))
                .findFirst().map(DagGraphView::of).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** A page of run history, newest first, optionally filtered by application / graph / status. */
    @GetMapping("/runs")
    public DagRunPage runs(@RequestParam(required = false) String application,
            @RequestParam(required = false) String graph,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<DagRunView> items = runLog.listRuns(application, graph, status, limit, offset);
        int total = runLog.countRuns(application, graph, status);
        return new DagRunPage(total, items);
    }

    /** One run drilled down: the run, its node executions and any child (subgraph) runs; 404 if unknown. */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<DagRunDetail> run(@PathVariable String runId) {
        DagRunView run = runLog.findRun(runId);
        if (run == null) {
            return ResponseEntity.notFound().build();
        }
        // Live frontier (in-flight nodes) only makes sense while the run is still going; on this node
        // it is known only when this scheduler is the one coordinating the run.
        List<String> running = List.of();
        EngineDagRunner engine = coordinator.getIfAvailable();
        if (engine != null && "RUNNING".equalsIgnoreCase(run.status())) {
            running = new java.util.ArrayList<>(engine.runningNodesOf(runId));
        }
        return ResponseEntity.ok(
                new DagRunDetail(run, runLog.nodesOf(runId), runLog.childRuns(runId), running));
    }

}
