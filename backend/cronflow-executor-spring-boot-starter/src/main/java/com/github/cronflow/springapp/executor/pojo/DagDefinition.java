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
package com.github.cronflow.springapp.executor.pojo;

import java.util.List;
import java.util.Map;

/**
 * The woven, serializable description of one DAG: exactly what is registered to the server and stored
 * in {@code cf_task_dag}. It carries only <b>data</b> — no code — so it round-trips through JSON.
 *
 * <p>
 * It intentionally mirrors the DAG kernel's own JSON schema (graph / inputs / channels / nodes /
 * edges / conditionals) but adds, per node, the <b>remote bean coordinates</b>
 * ({@link NodeDef#beanName()} / {@link NodeDef#methodName()}) the server dials over HTTP — the kernel's
 * model addresses nodes by class, whereas cronflow addresses them by "spring bean + method".
 *
 * @Description: DagDefinition
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagDefinition(String graph, List<String> inputs, List<ChannelDef> channels,
        List<NodeDef> nodes, List<EdgeDef> edges, List<ConditionalDef> conditionals,
        List<ShardDef> shards) {

    /** A state channel and the named reducer that merges concurrent writes to it. */
    public record ChannelDef(String name, String reducer) {}

    /** One node: its name, run policy, and either a remote bean+method or a nested {@code subgraph}. */
    public record NodeDef(String name, boolean entry, String trigger, int retries, String beanName,
            String methodName, String subgraph) {}

    /** A plain (non-branch) edge. {@code condition} is ON_SUCCESS / ON_FAILURE / ON_COMPLETE. */
    public record EdgeDef(String from, String to, String condition, String branch) {}

    /** A conditional (branch) switch kept whole so the server can rebuild the routing from SpEL. */
    public record ConditionalDef(List<String> sources, String form, String expression,
            List<String> predicates, Map<String, List<String>> branches, List<String> elseTargets) {}

    /** A dynamic fan-out (sharded) node: split the {@code input} collection, dispatch each shard to the
     *  node's bean+method across the cluster, gather the partials into {@code output}. */
    public record ShardDef(String node, String input, String output, int size) {}

}
