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
package com.github.cronflow.springapp.server;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chaconneai.openspreader.dag.CompiledGraph;
import com.chaconneai.openspreader.dag.GraphCatalog;
import com.chaconneai.openspreader.dag.GraphListener;
import com.chaconneai.openspreader.dag.GraphState;
import com.chaconneai.openspreader.dag.NodeOutcome;
import com.chaconneai.openspreader.dag.ProcessingDag;
import com.chaconneai.openspreader.dag.Reducer;
import com.chaconneai.openspreader.dag.RunResult;
import com.chaconneai.openspreader.dag.StateGraph;
import com.chaconneai.openspreader.dag.Trigger;
import tools.jackson.databind.ObjectMapper;
import com.github.cronflow.springapp.server.pojo.DagDefinition;

/**
 * Runs a cronflow DAG on the openspreader multi-processing engine: it builds an openspreader
 * {@link StateGraph} at runtime from the registered {@link DagDefinition} (each node backed by the
 * config-driven {@code cronflowNode} bean), binds it to the {@link ProcessingDag}, and invokes it — so
 * the DAG flows across the scheduler cluster while each node's target method runs in the executor over
 * HTTP. Replaces the earlier single-process coordinator.
 *
 * <p>
 * Skeleton scope: channels (+ reducers), plain on-success edges (fan-out / fan-in ALL) and entries.
 * Conditionals, non-ALL triggers, failure/complete edges and subgraphs are added next.
 *
 * @Description: EngineDagRunner
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class EngineDagRunner implements DagCoordinator, SubGraphResolver {

    private static final Logger log = LoggerFactory.getLogger(EngineDagRunner.class);

    private final ProcessingDag dagger;
    private final DagExecutorRegistry registry;
    private final DagRunLog runLog;
    private final ObjectMapper objectMapper;
    private final GraphCatalog catalog;
    private final Map<String, CompiledGraph> compiled = new ConcurrentHashMap<>();
    private final Set<String> unsupported = ConcurrentHashMap.newKeySet();
    /** Per-run monotonic seq for cf_dag_node_log rows (compiled graphs are shared across runs). */
    private final Map<String, AtomicInteger> nodeSeq =
            new ConcurrentHashMap<>();
    /** Nodes currently in flight per active run — the live frontier the console pulses on the graph.
     *  In-memory on the coordinating scheduler; a finished run has none. */
    private final Map<String, Set<String>> running = new ConcurrentHashMap<>();

    public EngineDagRunner(ProcessingDag dagger, DagExecutorRegistry registry, DagRunLog runLog,
            ObjectMapper objectMapper, List<Reducer<?>> customReducers) {
        this.dagger = dagger;
        this.registry = registry;
        this.runLog = runLog;
        this.objectMapper = objectMapper;
        GraphCatalog.Builder b = GraphCatalog.builder();
        for (Reducer<?> r : CronflowReducers.all()) {
            b.reducer(r);
        }
        for (Reducer<?> r : customReducers) {
            if (r.name() != null && !r.name().isBlank()) {
                b.reducer(r);
            }
        }
        this.catalog = b.build();
    }

    @Override
    public String trigger(String graph, Map<String, Object> initialState, String triggeredBy) {
        CompiledGraph flow = compiledFor(graph);
        if (flow == null) {
            return null;
        }
        String runId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();
        long t0 = System.nanoTime();
        String application = registry.applicationOf(graph).orElse(null);
        Map<String, Object> initial = initialState == null ? Map.of() : initialState;
        runLog.begin(runId, null, application, graph, triggeredBy, startedAt, json(initial));
        try {
            RunResult r = flow.invoke(GraphState.of(initial), runId);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            String status = r.failed() ? "FAILED" : "SUCCESS";
            int executed = r.completed() == null ? 0 : r.completed().size();
            String error = r.failed() && r.failure() != null ? r.failure().toString() : null;
            runLog.finish(runId, status, LocalDateTime.now(), ms, executed, r.failedNode(), error,
                    json(r.state().asMap()));
            log.info("cronflow: engine ran graph '{}' [{}] runId={}; final state: {}", graph, status,
                    runId, r.state().asMap());
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            runLog.finish(runId, "FAILED", LocalDateTime.now(), ms, 0, null, e.toString(), null);
            log.warn("cronflow: engine run of '{}' (runId={}) failed: {}", graph, runId, e.toString());
        } finally {
            nodeSeq.remove(runId);
            running.remove(runId);
            runLog.clearFrontier(runId);
        }
        return runId;
    }

    /** The nodes of {@code runId} currently in flight (empty once the run is over) — the console marks
     *  these RUNNING and pulses them on the graph. */
    public Set<String> runningNodesOf(String runId) {
        Set<String> s = running.get(runId);
        return s == null ? Set.of() : new HashSet<>(s);
    }

    /** Mirror the local in-flight set into the run-log so it replicates across the cluster: behind the
     *  round-robin console proxy, the run-detail query may land on a node that did not coordinate the
     *  run, and it still needs the frontier to pulse the live node(s). */
    private void pushFrontier(String runId) {
        Set<String> s = running.get(runId);
        try {
            runLog.frontier(runId, s == null ? Set.of() : new HashSet<>(s));
        } catch (RuntimeException e) {
            log.debug("cronflow: frontier push failed for {}: {}", runId, e.toString());
        }
    }

    @Override
    public Map<String, Object> runSubgraph(String graph, Map<String, Object> initialState,
            String parentRunId) {
        CompiledGraph flow = compiledFor(graph);
        if (flow == null) {
            throw new IllegalStateException("cronflow: subgraph '" + graph + "' has no runnable definition");
        }
        String subRunId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();
        long t0 = System.nanoTime();
        String application = registry.applicationOf(graph).orElse(null);
        Map<String, Object> init = initialState == null ? Map.of() : initialState;
        runLog.begin(subRunId, parentRunId, application, graph, "subgraph", startedAt, json(init));
        try {
            RunResult r = flow.invoke(GraphState.of(init), subRunId);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            String status = r.failed() ? "FAILED" : "SUCCESS";
            int executed = r.completed() == null ? 0 : r.completed().size();
            String error = r.failed() && r.failure() != null ? r.failure().toString() : null;
            runLog.finish(subRunId, status, LocalDateTime.now(), ms, executed, r.failedNode(), error,
                    json(r.state().asMap()));
            if (r.failed()) {
                throw new IllegalStateException("subgraph '" + graph + "' failed at node '"
                        + r.failedNode() + "'");
            }
            return r.state().asMap();
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            runLog.finish(subRunId, "FAILED", LocalDateTime.now(), ms, 0, null, e.toString(), null);
            throw e;
        } finally {
            nodeSeq.remove(subRunId);
            running.remove(subRunId);
            runLog.clearFrontier(subRunId);
        }
    }

    private CompiledGraph compiledFor(String graph) {
        if (unsupported.contains(graph)) {
            return null;
        }
        return compiled.computeIfAbsent(graph, this::build);
    }

    private CompiledGraph build(String graph) {
        DagDefinition def = registry.definition(graph);
        if (def == null) {
            log.warn("cronflow: no definition for '{}'; cannot run", graph);
            unsupported.add(graph);
            return null;
        }
        if (!isSupported(def)) {
            log.warn("cronflow: graph '{}' has no nodes; nothing to run", graph);
            unsupported.add(graph);
            return null;
        }
        StateGraph sg = StateGraph.create(graph);
        if (def.channels() != null) {
            for (DagDefinition.ChannelDef c : def.channels()) {
                Reducer<?> reducer = catalog.reducer(c.reducer());
                if (reducer == null) {
                    throw new IllegalStateException("unknown reducer '" + c.reducer() + "' on channel '"
                            + c.name() + "' of graph '" + graph + "'");
                }
                sg.channel(c.name(), cast(reducer));
            }
        }
        // Sharded nodes (dynamic fan-out) by node name, so the loop below can pick the right node bean.
        Map<String, DagDefinition.ShardDef> shardByNode = new LinkedHashMap<>();
        if (def.shards() != null) {
            for (DagDefinition.ShardDef s : def.shards()) {
                shardByNode.put(s.node(), s);
            }
        }
        // Nodes: a subgraph node runs a nested graph locally (cronflowSubGraph); a sharded node fans out
        // over MapReduce (cronflowShardedNode); every other node is a config-driven HTTP dispatch to the
        // executor (cronflowNode).
        for (DagDefinition.NodeDef n : def.nodes()) {
            DagDefinition.ShardDef shard = shardByNode.get(n.name());
            if (isSubgraph(n)) {
                sg.node(n.name(), "cronflowSubGraph", Map.of("subgraph", n.subgraph()));
                sg.local(n.name());
            } else if (shard != null) {
                sg.node(n.name(), "cronflowShardedNode", Map.of("input", shard.input(),
                        "output", shard.output(), "size", (long) shard.size(),
                        "bean", n.beanName(), "method", n.methodName()));
            } else {
                sg.node(n.name(), "cronflowNode",
                        Map.of("bean", n.beanName(), "method", n.methodName()));
            }
            if (n.retries() > 0) {
                sg.retry(n.name(), n.retries());
            }
        }
        // Join semantics (ALL is the default and needs nothing; ANY / AT_LEAST(n) are set explicitly).
        for (DagDefinition.NodeDef n : def.nodes()) {
            Trigger t = triggerOf(n.trigger());
            if (t != null) {
                sg.trigger(n.name(), t);
            }
        }
        // Plain edges, grouped by condition. A branch edge (part of a conditional) is skipped here — the
        // conditionals below own it.
        Map<String, Map<String, List<String>>> byCond = new LinkedHashMap<>();
        if (def.edges() != null) {
            for (DagDefinition.EdgeDef e : def.edges()) {
                if (e.branch() != null) {
                    continue;
                }
                String cond = e.condition() == null || e.condition().isBlank()
                        ? "ON_SUCCESS" : e.condition().trim().toUpperCase();
                byCond.computeIfAbsent(cond, k -> new LinkedHashMap<>())
                        .computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            }
        }
        byCond.forEach((cond, fromMap) -> fromMap.forEach((from, tos) -> {
            String[] targets = tos.toArray(new String[0]);
            switch (cond) {
                case "ON_FAILURE" -> sg.from(from).onFailure().to(targets);
                case "ON_COMPLETE" -> sg.from(from).onComplete().to(targets);
                default -> sg.from(from).to(targets);
            }
        }));
        // Conditional routing (SpEL): from(sources).when(expr).to(branch)…otherwise(else).
        if (def.conditionals() != null) {
            for (DagDefinition.ConditionalDef c : def.conditionals()) {
                applyConditional(sg, c);
            }
        }
        List<String> entries = new ArrayList<>();
        for (DagDefinition.NodeDef n : def.nodes()) {
            if (n.entry()) {
                entries.add(n.name());
            }
        }
        sg.entry(entries.toArray(new String[0]));
        sg.listener(new LogListener(graph));
        CompiledGraph flow = dagger.bind(sg.compile());
        log.info("cronflow: engine compiled graph '{}' ({} node(s))", graph, def.nodes().size());
        return flow;
    }

    /** Maps one cronflow {@link DagDefinition.ConditionalDef} onto the engine's when/switch routing. */
    private void applyConditional(StateGraph sg, DagDefinition.ConditionalDef c) {
        String[] sources = c.sources().toArray(new String[0]);
        StateGraph.From from = sg.from(sources);
        boolean isSwitch = "switch".equalsIgnoreCase(c.form()) && c.expression() != null;
        if (isSwitch) {
            StateGraph.Switch sw = from.switchOn(c.expression());
            if (c.branches() != null) {
                c.branches().forEach((key, targets) -> sw.caseOf(key, targets.toArray(new String[0])));
            }
            if (notEmpty(c.elseTargets())) {
                sw.orElse(c.elseTargets().toArray(new String[0]));
            }
            return;
        }
        // Predicate form: predicates[i] routes to branches["i"], in order; otherwise → elseTargets.
        List<String> preds = c.predicates() == null ? List.of() : c.predicates();
        StateGraph.When when = null;
        for (int i = 0; i < preds.size(); i++) {
            List<String> targets = c.branches() == null ? List.of()
                    : c.branches().getOrDefault(String.valueOf(i), List.of());
            when = (when == null ? from.when(preds.get(i)) : when.when(preds.get(i)))
                    .to(targets.toArray(new String[0]));
        }
        if (when != null && notEmpty(c.elseTargets())) {
            when.otherwise(c.elseTargets().toArray(new String[0]));
        }
    }

    /** Engine-runnable if it has nodes; conditionals, non-ALL triggers, failure edges and subgraphs are
     *  all supported now. A subgraph naming an unknown child still fails fast at run time. */
    private boolean isSupported(DagDefinition def) {
        return def.nodes() != null && !def.nodes().isEmpty();
    }

    private static boolean isSubgraph(DagDefinition.NodeDef n) {
        return n.subgraph() != null && !n.subgraph().isBlank();
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    /** ALL (default) → null (nothing to set); ANY → any(); AT_LEAST(n) → atLeast(n). */
    private static Trigger triggerOf(String spec) {
        if (spec == null) {
            return null;
        }
        String t = spec.trim().toUpperCase();
        if (t.isEmpty() || t.equals("ALL")) {
            return null;
        }
        if (t.equals("ANY")) {
            return Trigger.any();
        }
        if (t.startsWith("AT_LEAST")) {
            int lp = t.indexOf('(');
            int rp = t.indexOf(')');
            if (lp >= 0 && rp > lp) {
                try {
                    return Trigger.atLeast(Integer.parseInt(t.substring(lp + 1, rp).trim()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> Reducer<T> cast(Reducer<?> reducer) {
        return (Reducer<T>) reducer;
    }

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

    /**
     * Records each node execution into {@code cf_dag_node_log} as the engine finishes it — this is
     * what lets the console colour the graph and show, per node, where it ran and what it returned
     * (the node's bean/method is joined from the definition on the read side). It also logs the
     * {@code executedOn} label, which proves the node ran on another scheduler in the cluster.
     */
    private final class LogListener implements GraphListener {
        private final String graph;

        private LogListener(String graph) {
            this.graph = graph;
        }

        @Override
        public void onStart(String runId, CompiledGraph flow, GraphState initial) {
            running.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet());
            pushFrontier(runId);
        }

        @Override
        public void onTransition(String runId, String from, String to, GraphState state) {
            // 'to' is about to run — mark it the live frontier so the console can pulse it.
            if (to != null) {
                running.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet()).add(to);
                pushFrontier(runId);
            }
        }

        @Override
        public void onNodeFinished(String runId, NodeOutcome outcome) {
            log.info("cronflow: [{}] node '{}' finished on '{}' in {}ms (ok={})", graph,
                    outcome.node(), outcome.executedOn(), outcome.millis(), outcome.ok());
            Set<String> frontier = running.get(runId);
            if (frontier != null) {
                frontier.remove(outcome.node());
            }
            pushFrontier(runId);
            int seq = nodeSeq.computeIfAbsent(runId, k -> new AtomicInteger())
                    .getAndIncrement();
            String status = outcome.ok() ? "SUCCESS" : "FAILED";
            String output = outcome.updates() == null ? null : json(outcome.updates());
            try {
                runLog.node(runId, graph, outcome.node(), seq, status, null, output,
                        outcome.executedOn(), outcome.millis(), outcome.failureText());
            } catch (RuntimeException e) {
                log.debug("cronflow: could not write node-log for {}#{}: {}", runId, outcome.node(),
                        e.toString());
            }
        }

        @Override
        public void onFailure(RunResult result, Throwable cause) {
            log.warn("cronflow: [{}] run {} failed at '{}': {}", graph, result.runId(),
                    result.failedNode(), cause == null ? "?" : cause.toString());
        }
    }

}
