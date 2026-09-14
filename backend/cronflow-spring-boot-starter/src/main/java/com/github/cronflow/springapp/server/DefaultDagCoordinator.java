package com.github.cronflow.springapp.server;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chaconneai.openspreader.dag.Expressions;
import com.chaconneai.openspreader.dag.GraphCatalog;
import com.chaconneai.openspreader.dag.GraphState;
import com.chaconneai.openspreader.dag.Reducer;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs a stored DAG on the server, dispatching leaf nodes to executors over HTTP. Handles the real
 * DAG shapes:
 * <ul>
 *   <li>fan-out / fan-in with per-node {@code Trigger} (ALL / ANY / AT_LEAST(n));</li>
 *   <li>edge conditions (ON_SUCCESS / ON_FAILURE / ON_COMPLETE) and conditional (branch) routing via
 *       SpEL, both evaluated with the DAG kernel's {@code Expressions};</li>
 *   <li>per-channel merge via the kernel's named {@code Reducers} (concatList, writeOnce, …);</li>
 *   <li><b>subgraphs</b>: a node may run a nested DAG, seeded with the current state, whose result is
 *       merged back — supporting arbitrarily nested workflows;</li>
 *   <li>retries and skip propagation.</li>
 * </ul>
 * The kernel's own {@code GraphRunner} is not reused (it dispatches by class across the cluster); this
 * reuses the kernel's value/semantic classes and dispatches each node by HTTP instead.
 *
 * <p>TODO: parallel branch execution (runs sequentially now), on-failure compensation edges beyond
 * routing, and run-history persistence (cf_dag_run / cf_dag_node_log) + resume.
 *
 * @Description: DefaultDagCoordinator
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class DefaultDagCoordinator implements DagCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DefaultDagCoordinator.class);
    private static final int MAX_DEPTH = 20;
    private static final int RUN = 0, WAIT = 1, SKIP = 2;

    private final DagExecutorRegistry registry;
    private final DagNodeDispatcher dispatcher;
    private final DagRunLog runLog;
    private final ObjectMapper objectMapper;
    /** Holds the eight built-in reducers, looked up by the name each channel declares. */
    private final GraphCatalog reducers = GraphCatalog.builder().build();

    public DefaultDagCoordinator(DagExecutorRegistry registry, DagNodeDispatcher dispatcher,
            DagRunLog runLog, ObjectMapper objectMapper) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.runLog = runLog;
        this.objectMapper = objectMapper;
    }

    /** Serialize the state map to JSON for the run log's parameter/return-value columns. */
    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            return String.valueOf(value);
        }
    }

    private enum Status { SUCCESS, FAILED, SKIPPED }

    private enum ES { PENDING, TAKEN, NOT_TAKEN }

    private record Edge(String from, String to, boolean conditional, String condition, String branchKey) {}

    /** The outcome of one graph run. {@code runId} identifies it in cf_dag_log. */
    private record RunResult(Map<String, Object> state, boolean ok, String failedNode, String error,
            int executed, String runId) {}

    @Override
    public void trigger(String graph, Map<String, Object> initialState, String triggeredBy) {
        RunResult r = run(graph, initialState, 0, null, triggeredBy);
        if (r.state() != null) {
            log.info("cronflow: graph '{}' finished [{}] runId={}; final state: {}", graph,
                    r.ok() ? "SUCCESS" : "FAILED", r.runId(), r.state());
        }
    }

    /**
     * Runs {@code graph} (or a subgraph) to completion; records cf_dag_log (with {@code parentRunId}
     * for a subgraph) and one cf_dag_node_log row per node — traceable by runId / parent_run_id.
     */
    private RunResult run(String graph, Map<String, Object> initialState, int depth, String parentRunId,
            String triggeredBy) {
        String runId = UUID.randomUUID().toString();
        if (depth > MAX_DEPTH) {
            log.warn("cronflow: subgraph nesting exceeded {} at '{}'", MAX_DEPTH, graph);
            return new RunResult(null, false, null, "nesting too deep", 0, runId);
        }
        DagDefinition def = registry.definition(graph);
        if (def == null) {
            log.warn("cronflow: no definition for graph '{}'; cannot run", graph);
            return new RunResult(null, false, null, "no definition for '" + graph + "'", 0, runId);
        }
        LocalDateTime startedAt = LocalDateTime.now();
        long startNanos = System.nanoTime();
        String application = registry.applicationOf(graph).orElse(null);
        runLog.begin(runId, parentRunId, application, graph, triggeredBy, startedAt, json(initialState));
        java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger();

        Map<String, DagDefinition.NodeDef> nodes = new LinkedHashMap<>();
        def.nodes().forEach(n -> nodes.put(n.name(), n));

        Map<String, String> reducerByChannel = new HashMap<>();
        if (def.channels() != null) {
            def.channels().forEach(c -> reducerByChannel.put(c.name(), c.reducer()));
        }

        List<Edge> edges = new ArrayList<>();
        if (def.edges() != null) {
            for (DagDefinition.EdgeDef e : def.edges()) {
                edges.add(new Edge(e.from(), e.to(), false, e.condition(), null));
            }
        }
        Map<String, DagDefinition.ConditionalDef> condBySource = new HashMap<>();
        if (def.conditionals() != null) {
            for (DagDefinition.ConditionalDef c : def.conditionals()) {
                for (String src : c.sources()) {
                    condBySource.put(src, c);
                    if (c.branches() != null) {
                        c.branches().forEach((k, targets) -> targets
                                .forEach(t -> edges.add(new Edge(src, t, true, null, k))));
                    }
                    if (c.elseTargets() != null) {
                        c.elseTargets().forEach(t -> edges.add(new Edge(src, t, true, null, "else")));
                    }
                }
            }
        }

        Map<String, List<Edge>> outByFrom = new HashMap<>();
        Map<String, List<Edge>> inByTo = new HashMap<>();
        for (Edge e : edges) {
            outByFrom.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
            inByTo.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e);
        }

        Map<String, Object> state = new HashMap<>(initialState == null ? Map.of() : initialState);
        Map<String, Status> status = new HashMap<>();
        Map<Edge, ES> edgeState = new HashMap<>();
        edges.forEach(e -> edgeState.put(e, ES.PENDING));

        log.info("cronflow: {}running graph '{}' ({} node(s))", depth > 0 ? "[subgraph] " : "", graph,
                nodes.size());

        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (DagDefinition.NodeDef node : nodes.values()) {
                if (status.containsKey(node.name())) {
                    continue; // already terminal
                }
                int r = readiness(node, inByTo.getOrDefault(node.name(), List.of()), edgeState);
                if (r == RUN) {
                    Status s = runNode(graph, node, state, reducerByChannel, depth, runId, seq);
                    status.put(node.name(), s);
                    resolveOutbound(node.name(), s, outByFrom.getOrDefault(node.name(), List.of()),
                            edgeState, condBySource, state);
                    progressed = true;
                    break;
                } else if (r == SKIP) {
                    status.put(node.name(), Status.SKIPPED);
                    resolveOutbound(node.name(), Status.SKIPPED,
                            outByFrom.getOrDefault(node.name(), List.of()), edgeState, condBySource,
                            state);
                    runLog.node(runId, graph, node.name(), seq.incrementAndGet(), "SKIPPED",
                            json(state), null, null, 0L, null);
                    log.info("cronflow: graph '{}' node '{}' skipped", graph, node.name());
                    progressed = true;
                    break;
                }
            }
        }
        String failedNode = null;
        int executed = 0;
        for (Map.Entry<String, Status> en : status.entrySet()) {
            if (en.getValue() == Status.SUCCESS || en.getValue() == Status.FAILED) {
                executed++;
            }
            if (en.getValue() == Status.FAILED && failedNode == null) {
                failedNode = en.getKey();
            }
        }
        boolean ok = failedNode == null;
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        String error = ok ? null : "node '" + failedNode + "' failed";
        runLog.finish(runId, ok ? "SUCCESS" : "FAILED", LocalDateTime.now(), elapsedMs, executed,
                failedNode, error, json(state));
        return new RunResult(state, ok, failedNode, error, executed, runId);
    }

    private int readiness(DagDefinition.NodeDef node, List<Edge> inbound, Map<Edge, ES> edgeState) {
        if (inbound.isEmpty()) {
            return node.entry() ? RUN : SKIP;
        }
        int required = required(node.trigger());
        int taken = 0, resolved = 0;
        for (Edge e : inbound) {
            ES s = edgeState.get(e);
            if (s == ES.TAKEN) {
                taken++;
                resolved++;
            } else if (s == ES.NOT_TAKEN) {
                resolved++;
            }
        }
        int pending = inbound.size() - resolved;
        if (required == 0) { // ALL
            if (pending > 0) {
                return WAIT;
            }
            return taken > 0 ? RUN : SKIP;
        }
        if (taken >= required) {
            return RUN;
        }
        return pending == 0 ? SKIP : WAIT;
    }

    /** 0 == ALL (every inbound edge); n == at least n; ANY is 1. */
    private int required(String trigger) {
        if (trigger == null) {
            return 0;
        }
        String t = trigger.trim().toUpperCase();
        if (t.equals("ANY")) {
            return 1;
        }
        if (t.startsWith("AT_LEAST(") && t.endsWith(")")) {
            try {
                return Integer.parseInt(t.substring("AT_LEAST(".length(), t.length() - 1).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0; // ALL
    }

    private Status runNode(String graph, DagDefinition.NodeDef node, Map<String, Object> state,
            Map<String, String> reducerByChannel, int depth, String runId,
            java.util.concurrent.atomic.AtomicInteger seq) {
        String inputParam = json(state);
        long t0 = System.nanoTime();
        if (node.subgraph() != null && !node.subgraph().isBlank()) {
            RunResult sub = run(node.subgraph(), new HashMap<>(state), depth + 1, runId, "subgraph");
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (!sub.ok() || sub.state() == null) {
                runLog.node(runId, graph, node.name(), seq.incrementAndGet(), "FAILED", inputParam,
                        null, "subgraph:" + sub.runId(), ms, sub.error());
                log.warn("cronflow: graph '{}' subgraph node '{}' ({}) failed", graph, node.name(),
                        node.subgraph());
                return Status.FAILED;
            }
            state.putAll(sub.state()); // subgraph was seeded with our state, so its channels supersede
            runLog.node(runId, graph, node.name(), seq.incrementAndGet(), "SUCCESS", inputParam,
                    json(sub.state()), "subgraph:" + sub.runId(), ms, null);
            log.info("cronflow: graph '{}' node '{}' ok (subgraph '{}')", graph, node.name(),
                    node.subgraph());
            return Status.SUCCESS;
        }
        DagNodeDispatcher.Dispatched d = dispatchWithRetry(graph, node, state);
        NodeRunResult res = d.result();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (res.ok()) {
            merge(state, res.updates(), reducerByChannel);
            runLog.node(runId, graph, node.name(), seq.incrementAndGet(), "SUCCESS", inputParam,
                    json(res.updates()), d.executor(), ms, null);
            log.info("cronflow: graph '{}' node '{}' ok ({} channel(s) changed)", graph, node.name(),
                    res.updates() == null ? 0 : res.updates().size());
            return Status.SUCCESS;
        }
        runLog.node(runId, graph, node.name(), seq.incrementAndGet(), "FAILED", inputParam, null,
                d.executor(), ms, res.errorDetail());
        log.warn("cronflow: graph '{}' node '{}' failed: {}", graph, node.name(), res.errorDetail());
        return Status.FAILED;
    }

    private DagNodeDispatcher.Dispatched dispatchWithRetry(String graph, DagDefinition.NodeDef node,
            Map<String, Object> state) {
        int attempts = 1 + Math.max(0, node.retries());
        DagNodeDispatcher.Dispatched d =
                new DagNodeDispatcher.Dispatched(new NodeRunResult(false, Map.of(), "not dispatched"), null);
        for (int a = 0; a < attempts; a++) {
            try {
                d = dispatcher.dispatch(graph, node, new HashMap<>(state));
                if (d.result().ok()) {
                    return d;
                }
            } catch (RuntimeException ex) {
                d = new DagNodeDispatcher.Dispatched(new NodeRunResult(false, Map.of(), ex.toString()),
                        null);
            }
        }
        return d;
    }

    @SuppressWarnings("unchecked")
    private void merge(Map<String, Object> state, Map<String, Object> updates,
            Map<String, String> reducerByChannel) {
        if (updates == null) {
            return;
        }
        for (Map.Entry<String, Object> u : updates.entrySet()) {
            String channel = u.getKey();
            Object incoming = u.getValue();
            String reducerName = reducerByChannel.get(channel);
            Object current = state.get(channel);
            if (reducerName != null && current != null) {
                Reducer<?> reducer = reducers.reducer(reducerName);
                if (reducer != null) {
                    incoming = ((Reducer<Object>) reducer).reduce(current, incoming);
                }
            }
            state.put(channel, incoming);
        }
    }

    private void resolveOutbound(String nodeName, Status status, List<Edge> outbound,
            Map<Edge, ES> edgeState, Map<String, DagDefinition.ConditionalDef> condBySource,
            Map<String, Object> state) {
        Set<String> chosen = null;
        DagDefinition.ConditionalDef c = condBySource.get(nodeName);
        if (c != null && status == Status.SUCCESS) {
            chosen = evaluate(c, state);
        }
        for (Edge e : outbound) {
            ES st;
            if (e.conditional()) {
                st = status == Status.SUCCESS && chosen != null && chosen.contains(e.branchKey())
                        ? ES.TAKEN : ES.NOT_TAKEN;
            } else {
                st = carriesOn(e.condition(), status) ? ES.TAKEN : ES.NOT_TAKEN;
            }
            edgeState.put(e, st);
        }
    }

    /** Evaluate a conditional against the current state, returning the chosen branch key(s). */
    private Set<String> evaluate(DagDefinition.ConditionalDef c, Map<String, Object> state) {
        Set<String> chosen = new HashSet<>();
        GraphState gs = GraphState.of(state);
        try {
            if (c.predicates() != null && !c.predicates().isEmpty()) { // predicate (when) form
                for (int i = 0; i < c.predicates().size(); i++) {
                    String spel = c.predicates().get(i);
                    if (spel != null && Expressions.predicate(spel).test(gs)) {
                        chosen.add(String.valueOf(i));
                        return chosen;
                    }
                }
            } else if (c.expression() != null) { // switch form
                String key = Expressions.router(c.expression()).apply(gs);
                if (key != null && c.branches() != null && c.branches().containsKey(key)) {
                    chosen.add(key);
                    return chosen;
                }
            }
        } catch (RuntimeException e) {
            log.warn("cronflow: conditional evaluation failed ({}); routing to else", e.toString());
        }
        chosen.add("else");
        return chosen;
    }

    private boolean carriesOn(String condition, Status status) {
        return switch (condition == null ? "ON_SUCCESS" : condition) {
            case "ON_FAILURE" -> status == Status.FAILED;
            case "ON_COMPLETE" -> status == Status.SUCCESS || status == Status.FAILED;
            default -> status == Status.SUCCESS; // ON_SUCCESS
        };
    }

}
