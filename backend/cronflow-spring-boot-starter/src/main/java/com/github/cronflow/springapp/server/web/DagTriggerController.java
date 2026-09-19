package com.github.cronflow.springapp.server.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.server.DagCoordinator;
import com.github.cronflow.springapp.server.DagExecutorRegistry;

/**
 * Manual DAG trigger for the console and for operators: a DAG normally runs when its bound cronsmith
 * {@code @Task} finishes, but this endpoint kicks the same graph off on demand with a caller-supplied
 * initial state. It goes through the very same {@link DagCoordinator} the task listener uses, so the
 * flow still runs distributed across the scheduler cluster on the openspreader engine — the trigger is
 * the only thing that differs.
 *
 * <p>
 * Any scheduler node accepts the call: the engine invokes on whichever node received it and dispatches
 * the nodes across the cluster from there (it does not have to be the leader).
 *
 * @Description: DagTriggerController
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@RestController
public class DagTriggerController {

    private final DagExecutorRegistry registry;
    private final DagCoordinator coordinator;

    public DagTriggerController(DagExecutorRegistry registry, DagCoordinator coordinator) {
        this.registry = registry;
        this.coordinator = coordinator;
    }

    /**
     * Trigger {@code graph} with an optional JSON body as its initial channel state.
     *
     * <ul>
     * <li>404 — no graph by that name is registered</li>
     * <li>422 — the graph exists but the engine cannot run it yet (e.g. conditionals / subgraph in the
     * current skeleton)</li>
     * <li>200 — accepted; the body carries the {@code runId} to follow the run by</li>
     * </ul>
     */
    @PostMapping("/dags/{graph}/trigger")
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable String graph,
            @RequestBody(required = false) Map<String, Object> initialState) {
        if (registry.definition(graph) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("graph", graph, "error", "no such graph"));
        }
        String runId = coordinator.trigger(graph, initialState == null ? Map.of() : initialState,
                "manual");
        if (runId == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("graph", graph,
                    "error", "graph is not runnable by the engine yet"));
        }
        return ResponseEntity.ok(Map.of("graph", graph, "runId", runId, "triggeredBy", "manual"));
    }

}
