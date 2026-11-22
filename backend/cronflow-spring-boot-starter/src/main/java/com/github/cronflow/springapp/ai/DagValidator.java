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
package com.github.cronflow.springapp.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagDefinition.ChannelDef;
import com.github.cronflow.springapp.pojo.DagDefinition.ConditionalDef;
import com.github.cronflow.springapp.pojo.DagDefinition.EdgeDef;
import com.github.cronflow.springapp.pojo.DagDefinition.NodeDef;
import com.github.cronflow.springapp.pojo.DagDefinition.ShardDef;

/**
 * Validates a generated {@link DagDefinition} against the engine's rules and a {@link NodeCatalog},
 * before any human sees it. Two gates: shape (required fields, uniqueness) and semantics (nodes exist in
 * the catalog, edges reference real nodes, the graph is acyclic, channels and reducers are valid,
 * conditionals and shards target real nodes). Returns a list of issues, empty when the DAG is valid.
 *
 * <p>
 * Pure and side-effect free, so it drives both the repair loop and a unit test suite with no model or
 * network involved.
 */
public class DagValidator {

    /** The reducers the engine ships (see CronflowReducers). A blank reducer means last-write-wins. */
    public static final Set<String> KNOWN_REDUCERS =
            Set.of("max", "min", "and", "or", "concatString", "joinCsv");

    private static final Set<String> KNOWN_TRIGGERS = Set.of("ALL", "ANY");

    /** One validation problem, anchored at a JSON-ish path for a precise repair prompt. */
    public record Issue(String path, String message) {
        @Override
        public String toString() {
            return path + ": " + message;
        }
    }

    public List<Issue> validate(DagDefinition dag, NodeCatalog catalog) {
        List<Issue> issues = new ArrayList<>();
        if (dag == null) {
            issues.add(new Issue("$", "the DAG is null"));
            return issues;
        }
        if (isBlank(dag.graph())) {
            issues.add(new Issue("graph", "graph name is required"));
        }
        List<NodeDef> nodes = dag.nodes();
        if (nodes == null || nodes.isEmpty()) {
            issues.add(new Issue("nodes", "a DAG needs at least one node"));
            return issues;
        }

        Set<String> nodeNames = new HashSet<>();
        boolean hasEntry = false;
        for (int i = 0; i < nodes.size(); i++) {
            NodeDef n = nodes.get(i);
            String at = "nodes[" + i + "]";
            if (n == null || isBlank(n.name())) {
                issues.add(new Issue(at, "node name is required"));
                continue;
            }
            if (!nodeNames.add(n.name())) {
                issues.add(new Issue(at, "duplicate node name '" + n.name() + "'"));
            }
            if (n.entry()) {
                hasEntry = true;
            }
            if (!isBlank(n.trigger()) && !KNOWN_TRIGGERS.contains(n.trigger())) {
                issues.add(new Issue(at + ".trigger",
                        "trigger must be ALL or ANY, was '" + n.trigger() + "'"));
            }
            if (n.retries() < 0) {
                issues.add(new Issue(at + ".retries", "retries must be zero or more"));
            }
            // A node runs one of four ways: an HTTP call to a URL, a script, a nested subgraph, or a
            // catalog bean. Only a plain bean node is constrained to the catalog.
            if (n.isHttp()) {
                if (n.endpoint() == null || isBlank(n.endpoint().url())) {
                    issues.add(new Issue(at + ".endpoint",
                            "http node '" + n.name() + "' needs an endpoint url"));
                }
            } else if (n.isScript()) {
                if (isBlank(n.script())) {
                    issues.add(new Issue(at + ".script",
                            "script node '" + n.name() + "' needs a script body"));
                }
            } else if (isBlank(n.subgraph())) {
                if (catalog == null || !catalog.has(n.name())) {
                    issues.add(new Issue(at, "node '" + n.name()
                            + "' is not in the catalog; use only catalog nodes, an http node (with an "
                            + "endpoint url), or a script node"));
                } else {
                    CatalogNode c = catalog.byName(n.name());
                    if (!isBlank(n.beanName()) && !n.beanName().equals(c.beanName())) {
                        issues.add(new Issue(at + ".beanName", "beanName '" + n.beanName()
                                + "' does not match catalog node '" + n.name() + "'"));
                    }
                    if (!isBlank(n.methodName()) && !n.methodName().equals(c.methodName())) {
                        issues.add(new Issue(at + ".methodName", "methodName '" + n.methodName()
                                + "' does not match catalog node '" + n.name() + "'"));
                    }
                }
            }
        }
        if (!hasEntry) {
            issues.add(new Issue("nodes", "no entry node; mark at least one node entry=true"));
        }

        validateChannels(dag.channels(), issues);
        validateEdges(dag.edges(), nodeNames, issues);
        validateConditionals(dag.conditionals(), nodeNames, issues);
        validateShards(dag.shards(), nodeNames, issues);
        detectCycle(dag.edges(), nodeNames, issues);
        return issues;
    }

    private void validateChannels(List<ChannelDef> channels, List<Issue> issues) {
        if (channels == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < channels.size(); i++) {
            ChannelDef c = channels.get(i);
            String at = "channels[" + i + "]";
            if (c == null || isBlank(c.name())) {
                issues.add(new Issue(at, "channel name is required"));
                continue;
            }
            if (!seen.add(c.name())) {
                issues.add(new Issue(at, "duplicate channel '" + c.name() + "'"));
            }
            if (!isBlank(c.reducer()) && !KNOWN_REDUCERS.contains(c.reducer())) {
                issues.add(new Issue(at + ".reducer", "unknown reducer '" + c.reducer()
                        + "'; use one of " + KNOWN_REDUCERS + " or leave blank for last-write-wins"));
            }
        }
    }

    private void validateEdges(List<EdgeDef> edges, Set<String> nodeNames, List<Issue> issues) {
        if (edges == null) {
            return;
        }
        for (int i = 0; i < edges.size(); i++) {
            EdgeDef e = edges.get(i);
            String at = "edges[" + i + "]";
            if (e == null) {
                issues.add(new Issue(at, "edge is null"));
                continue;
            }
            if (!nodeNames.contains(e.from())) {
                issues.add(new Issue(at + ".from", "edge from unknown node '" + e.from() + "'"));
            }
            if (!nodeNames.contains(e.to())) {
                issues.add(new Issue(at + ".to", "edge to unknown node '" + e.to() + "'"));
            }
        }
    }

    private void validateConditionals(List<ConditionalDef> conditionals, Set<String> nodeNames,
            List<Issue> issues) {
        if (conditionals == null) {
            return;
        }
        for (int i = 0; i < conditionals.size(); i++) {
            ConditionalDef c = conditionals.get(i);
            String at = "conditionals[" + i + "]";
            if (c == null) {
                issues.add(new Issue(at, "conditional is null"));
                continue;
            }
            if (c.branches() != null) {
                for (Map.Entry<String, List<String>> b : c.branches().entrySet()) {
                    if (b.getValue() != null) {
                        for (String target : b.getValue()) {
                            if (!nodeNames.contains(target)) {
                                issues.add(new Issue(at + ".branches." + b.getKey(),
                                        "branch targets unknown node '" + target + "'"));
                            }
                        }
                    }
                }
            }
            if (c.elseTargets() != null) {
                for (String target : c.elseTargets()) {
                    if (!nodeNames.contains(target)) {
                        issues.add(new Issue(at + ".elseTargets",
                                "else targets unknown node '" + target + "'"));
                    }
                }
            }
        }
    }

    private void validateShards(List<ShardDef> shards, Set<String> nodeNames, List<Issue> issues) {
        if (shards == null) {
            return;
        }
        for (int i = 0; i < shards.size(); i++) {
            ShardDef s = shards.get(i);
            String at = "shards[" + i + "]";
            if (s == null) {
                issues.add(new Issue(at, "shard is null"));
                continue;
            }
            if (!nodeNames.contains(s.node())) {
                issues.add(new Issue(at + ".node", "shard targets unknown node '" + s.node() + "'"));
            }
            if (s.size() <= 0) {
                issues.add(new Issue(at + ".size", "shard size must be greater than zero"));
            }
        }
    }

    /** Depth-first cycle detection over the edge graph. Subgraph loops are out of scope for v1. */
    private void detectCycle(List<EdgeDef> edges, Set<String> nodeNames, List<Issue> issues) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        Map<String, List<String>> adj = new HashMap<>();
        for (EdgeDef e : edges) {
            if (e != null && nodeNames.contains(e.from()) && nodeNames.contains(e.to())) {
                adj.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            }
        }
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        for (String start : nodeNames) {
            if (hasCycle(start, adj, visited, inStack)) {
                issues.add(new Issue("edges", "the graph has a cycle through '" + start
                        + "'; a DAG must be acyclic"));
                return;
            }
        }
    }

    private boolean hasCycle(String node, Map<String, List<String>> adj, Set<String> visited,
            Set<String> inStack) {
        if (inStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visited.add(node);
        inStack.add(node);
        for (String next : adj.getOrDefault(node, List.of())) {
            if (hasCycle(next, adj, visited, inStack)) {
                return true;
            }
        }
        inStack.remove(node);
        return false;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
