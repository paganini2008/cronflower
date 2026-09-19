package com.github.cronflow.springapp.server.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.server.ClusterDagRegistry;
import com.github.cronflow.springapp.server.DagExecutorRegistry;
import com.github.cronflow.springapp.server.DagExecutorRegistry.ExecutorInstance;
import com.github.cronflow.springapp.server.pojo.DagCreateRequest;
import com.github.cronflow.springapp.server.pojo.DagDefinition;
import com.github.cronflow.springapp.server.pojo.DagRegistrationRequest;

/**
 * Authoring API for the console's DAG canvas: create a graph by drawing it, rather than by annotating
 * an executor. The definition is registered against a live executor of the chosen application (so its
 * nodes' beans/methods resolve there) and gossips across the cluster like any other registration, so
 * it can be triggered immediately — manually, or by a bound task later.
 *
 * @Description: DagAuthoringController
 * @Author: Fred Feng
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
        if (request.application() == null || request.application().isBlank()) {
            return bad("an application is required");
        }
        ExecutorInstance host = registry.anyLiveInstance(request.application()).orElse(null);
        if (host == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "error", "no live executor for application '" + request.application() + "'"));
        }
        DagRegistrationRequest reg = new DagRegistrationRequest(request.application(),
                host.instanceId(), host.runUrl(), host.healthCheckUrl(), List.of(def), null,
                host.weight());
        clusterRegistry.register(reg);
        return ResponseEntity.ok(Map.of("graph", def.graph(), "application", request.application()));
    }

    private ResponseEntity<Map<String, Object>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

}
