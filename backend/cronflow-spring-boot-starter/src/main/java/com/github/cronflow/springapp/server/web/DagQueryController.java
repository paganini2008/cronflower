/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cronflow.springapp.server.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.server.DagExecutorRegistry;
import com.github.cronflow.springapp.server.DagRunLog;
import com.github.cronflow.springapp.server.EngineDagRunner;
import com.github.cronflow.springapp.server.pojo.DagGraphPage;
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

    /** A page of registered graphs (for the DAG list + diagram), newest-name first, optionally filtered
     *  by a substring of the graph or application name. Server-side paged like the Tasks / Runs lists. */
    @GetMapping("/dags")
    public DagGraphPage dags(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<DagGraphView> all = registry.graphs().stream().map(DagGraphView::of)
                .filter(v -> needle.isEmpty() || v.graph().toLowerCase().contains(needle)
                        || v.application().toLowerCase().contains(needle))
                .sorted(java.util.Comparator.comparing(DagGraphView::graph))
                .toList();
        int from = Math.min(Math.max(offset, 0), all.size());
        int to = limit <= 0 ? all.size() : Math.min(from + limit, all.size());
        return new DagGraphPage(all.size(), all.subList(from, to));
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
        // Live frontier (in-flight nodes), read from the run-log which replicates it across the cluster,
        // so this query answers correctly on ANY node behind the round-robin console proxy — not only on
        // the node that coordinated the run. Only meaningful while the run is still going.
        List<String> running = List.of();
        if ("RUNNING".equalsIgnoreCase(run.status())) {
            running = new ArrayList<>(runLog.frontierOf(runId));
        }
        return ResponseEntity.ok(
                new DagRunDetail(run, runLog.nodesOf(runId), runLog.childRuns(runId), running));
    }

}
