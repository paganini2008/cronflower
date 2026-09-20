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
package com.github.cronflow.springapp.executor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.github.cronflow.springapp.executor.pojo.DagDefinition;
import com.github.cronflow.springapp.executor.pojo.TriggerBinding;

/**
 * The programmatic way to weave a DAG, as an alternative to the {@link Dag}/{@link DagNode}
 * annotations. Declare a {@code @Bean CronflowDag} and the executor registers it with the server the
 * same way it registers annotated graphs — both produce a {@link DagDefinition}.
 *
 * <p>
 * Each node names its own {@code bean} + {@code method}, so one graph can span several service beans.
 * The {@code @Bean} returns the {@code CronflowDag} itself (the executor calls {@code build()}), so end
 * the chain on a graph-level step — {@link #triggeredBy} / {@link #channel} / {@link #input} — not on
 * {@code build()} (which returns the {@link DagDefinition}). Bind a {@code @Task} with
 * {@link #triggeredBy} so the graph actually runs.
 *
 * <pre>{@code
 * @Bean
 * CronflowDag orderFlow() {
 *     return CronflowDag.define("order-flow")
 *             .input("orderId")
 *             .channel("steps", "concatList")
 *             .node("validate").bean("orderFlow").method("validate").entry().to("reserve", "charge")
 *             .node("charge").bean("orderFlow").method("charge").to("riskScore")
 *             .node("riskScore").bean("orderFlow").method("riskScore")
 *                 .when("#risk > 80", "humanReview").otherwise("fulfilment")
 *             .node("transfer").bean("bank").method("transfer").retry(2).onFailure("flag")
 *             .triggeredBy("cronsmith-executor", "orderFlow.kickoff");
 * }
 * }</pre>
 *
 * @Description: CronflowDag
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public final class CronflowDag {

    private final String graph;
    private final List<String> inputs = new ArrayList<>();
    private final List<DagDefinition.ChannelDef> channels = new ArrayList<>();
    private final List<NodeAcc> nodes = new ArrayList<>();
    private final List<TriggerBinding> triggers = new ArrayList<>();

    private CronflowDag(String graph) {
        this.graph = graph;
    }

    public static CronflowDag define(String graph) {
        return new CronflowDag(graph);
    }

    public CronflowDag input(String channel) {
        inputs.add(channel);
        return this;
    }

    /**
     * Bind a cronsmith {@code @Task} (by its group + name) to this graph, so the task's completion
     * triggers a run with its return value as the initial state — the programmatic equivalent of putting
     * a {@code @Task} in a {@code @Dag} class. Call once per triggering task.
     */
    public CronflowDag triggeredBy(String taskGroup, String taskName) {
        triggers.add(new TriggerBinding(taskGroup, taskName, graph));
        return this;
    }

    /** The task→graph trigger bindings declared via {@link #triggeredBy}, for the scanner to register. */
    public List<TriggerBinding> triggerBindings() {
        return List.copyOf(triggers);
    }

    public CronflowDag channel(String name, String reducer) {
        channels.add(new DagDefinition.ChannelDef(name, reducer));
        return this;
    }

    /** Begins a node; chain its config on the returned builder, then {@code node(...)} again or {@code build()}. */
    public NodeBuilder node(String name) {
        NodeAcc acc = new NodeAcc(name);
        nodes.add(acc);
        return new NodeBuilder(this, acc);
    }

    /** Flattens the accumulated nodes/edges/conditionals into the serializable definition. */
    public DagDefinition build() {
        List<DagDefinition.NodeDef> nodeDefs = new ArrayList<>();
        List<DagDefinition.EdgeDef> edgeDefs = new ArrayList<>();
        List<DagDefinition.ConditionalDef> conditionalDefs = new ArrayList<>();
        for (NodeAcc n : nodes) {
            nodeDefs.add(new DagDefinition.NodeDef(n.name, n.entry, n.trigger, n.retries, n.beanName,
                    n.methodName, n.subgraph));
            n.to.forEach(t -> edgeDefs.add(new DagDefinition.EdgeDef(n.name, t, "ON_SUCCESS", null)));
            n.onFailure.forEach(t -> edgeDefs.add(new DagDefinition.EdgeDef(n.name, t, "ON_FAILURE", null)));
            n.onComplete.forEach(t -> edgeDefs.add(new DagDefinition.EdgeDef(n.name, t, "ON_COMPLETE", null)));
            if (!n.whens.isEmpty() || !n.otherwise.isEmpty()) {
                List<String> predicates = new ArrayList<>();
                Map<String, List<String>> branches = new LinkedHashMap<>();
                for (int i = 0; i < n.whens.size(); i++) {
                    predicates.add(n.whens.get(i).expr);
                    branches.put(String.valueOf(i), List.copyOf(n.whens.get(i).to));
                }
                conditionalDefs.add(new DagDefinition.ConditionalDef(List.of(n.name), "predicate",
                        null, predicates, branches, List.copyOf(n.otherwise)));
            }
        }
        return new DagDefinition(graph, List.copyOf(inputs), List.copyOf(channels),
                List.copyOf(nodeDefs), List.copyOf(edgeDefs), List.copyOf(conditionalDefs), List.of());
    }

    /** Chained node configuration; re-exposes graph-level steps so the chain never breaks. */
    public static final class NodeBuilder {

        private final CronflowDag owner;
        private final NodeAcc acc;

        private NodeBuilder(CronflowDag owner, NodeAcc acc) {
            this.owner = owner;
            this.acc = acc;
        }

        public NodeBuilder bean(String beanName) { acc.beanName = beanName; return this; }
        public NodeBuilder method(String methodName) { acc.methodName = methodName; return this; }
        public NodeBuilder subgraph(String graph) { acc.subgraph = graph; return this; }
        public NodeBuilder entry() { acc.entry = true; return this; }
        public NodeBuilder trigger(String trigger) { acc.trigger = trigger; return this; }
        public NodeBuilder retry(int times) { acc.retries = times; return this; }
        public NodeBuilder to(String... targets) { acc.to.addAll(List.of(targets)); return this; }
        public NodeBuilder onFailure(String... targets) { acc.onFailure.addAll(List.of(targets)); return this; }
        public NodeBuilder onComplete(String... targets) { acc.onComplete.addAll(List.of(targets)); return this; }
        public NodeBuilder when(String expr, String... targets) { acc.whens.add(new WhenAcc(expr, List.of(targets))); return this; }
        public NodeBuilder otherwise(String... targets) { acc.otherwise.addAll(List.of(targets)); return this; }

        // Re-exposed graph-level steps.
        public NodeBuilder node(String name) { return owner.node(name); }
        public CronflowDag input(String channel) { return owner.input(channel); }
        public CronflowDag channel(String name, String reducer) { return owner.channel(name, reducer); }
        public CronflowDag triggeredBy(String taskGroup, String taskName) { return owner.triggeredBy(taskGroup, taskName); }
        public DagDefinition build() { return owner.build(); }
    }

    private static final class NodeAcc {
        private final String name;
        private boolean entry;
        private String trigger = "ALL";
        private int retries;
        private String beanName;
        private String methodName;
        private String subgraph;
        private final List<String> to = new ArrayList<>();
        private final List<String> onFailure = new ArrayList<>();
        private final List<String> onComplete = new ArrayList<>();
        private final List<WhenAcc> whens = new ArrayList<>();
        private final List<String> otherwise = new ArrayList<>();

        private NodeAcc(String name) {
            this.name = name;
        }
    }

    private static final class WhenAcc {
        private final String expr;
        private final List<String> to;

        private WhenAcc(String expr, List<String> to) {
            this.expr = expr;
            this.to = to;
        }
    }

}
