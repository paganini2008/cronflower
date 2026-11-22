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
package com.github.cronflow.springapp.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.dag.ClusterDagRegistry;
import com.github.cronflow.springapp.dag.DagExecutorRegistry;
import com.github.cronflow.springapp.dag.DagExecutorRegistry.ExecutorInstance;
import com.github.cronflow.springapp.pojo.DagCreateRequest;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagRegistrationRequest;

/**
 * Authoring API for the console's DAG canvas: create a graph by drawing it, rather than by annotating
 * an executor. The definition is registered against a live executor of the chosen application (so its
 * nodes' beans/methods resolve there) and gossips across the cluster like any other registration, so
 * it can be triggered immediately — manually, or by a bound task later.
 *
 * @Description: DagAuthoringController
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
@RestController
public class DagAuthoringController {

    private final ClusterDagRegistry clusterRegistry;
    private final DagExecutorRegistry registry;

    public DagAuthoringController(ClusterDagRegistry clusterRegistry, DagExecutorRegistry registry) {
        this.clusterRegistry = clusterRegistry;
        this.registry = registry;
    }

    /** The applications the canvas can target — those with a live executor to host the node beans. */
    @GetMapping("/applications")
    public List<String> applications() {
        return registry.liveApplications();
    }

    /**
     * Create (or replace) a console-authored graph.
     *
     * <ul>
     * <li>400 — no graph name, or no nodes</li>
     * <li>422 — the chosen application has no live executor to host the graph</li>
     * <li>200 — created; the body echoes the graph name</li>
     * </ul>
     */
    @PostMapping("/dags")
    public ResponseEntity<Map<String, Object>> create(@RequestBody DagCreateRequest request) {
        DagDefinition def = request == null ? null : request.definition();
        if (def == null || def.graph() == null || def.graph().isBlank()) {
            return bad("a graph name is required");
        }
        if (def.nodes() == null || def.nodes().isEmpty()) {
            return bad("a graph needs at least one node");
        }
        // A pure-HTTP graph (every node is an HTTP call the scheduler makes itself) needs no executor,
        // so it registers host-less and even without an application; a graph with bean/script nodes
        // still needs a live executor of its application to dispatch to.
        boolean needsExecutor = needsExecutor(def);
        String application = request.application() == null || request.application().isBlank()
                ? (needsExecutor ? null : "http")
                : request.application();
        if (needsExecutor && application == null) {
            return bad("an application is required");
        }
        if (!needsExecutor) {
            registry.registerDefinition(application, def);
            return ResponseEntity.ok(Map.of("graph", def.graph(), "application", application));
        }
        ExecutorInstance host = registry.anyLiveInstance(application).orElse(null);
        if (host == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "error", "no live executor for application '" + application + "'"));
        }
        DagRegistrationRequest reg = new DagRegistrationRequest(application,
                host.instanceId(), host.runUrl(), host.healthCheckUrl(), List.of(def), null,
                host.weight());
        clusterRegistry.register(reg);
        return ResponseEntity.ok(Map.of("graph", def.graph(), "application", application));
    }

    /** True when the graph has a bean/script node (runs on an executor); HTTP and subgraph nodes do not. */
    private static boolean needsExecutor(DagDefinition def) {
        if (def.nodes() == null) {
            return false;
        }
        return def.nodes().stream().anyMatch(n -> !n.isHttp()
                && (n.subgraph() == null || n.subgraph().isBlank()));
    }

    private ResponseEntity<Map<String, Object>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

}
