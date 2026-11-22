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
package com.github.cronflow.springapp.pojo;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Server-side copy of the woven DAG description received from an executor and stored in
 * {@code cf_task_dag}. Same schema as the executor's {@code DagDefinition} (deliberately duplicated
 * across modules, as the scheduler duplicates its request DTOs).
 *
 * @Description: DagDefinition
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
public record DagDefinition(String graph, List<String> inputs, List<ChannelDef> channels,
        List<NodeDef> nodes, List<EdgeDef> edges, List<ConditionalDef> conditionals,
        List<ShardDef> shards)
        implements Serializable {

    public record ChannelDef(String name, String reducer) implements Serializable {}

    /**
     * A node of the graph. A plain node runs on a Java executor (its {@code beanName}+{@code methodName}
     * invoked by reflection). Two additions make it host-agnostic:
     * <ul>
     * <li>{@code application} names which executor application runs the node; absent means the graph's
     * owning application, so naming a different one makes the DAG cross-application.</li>
     * <li>{@code kind}={@code http} with an {@code endpoint} makes the node a direct HTTP call the
     * scheduler performs itself (no executor) — the target can be any service (Python/Go/Rust/...): we
     * only speak HTTP to it. {@code io} maps channels to the request and the response back to channels;
     * when absent the merged channel state is sent as the body and the JSON response object is taken as
     * the channel updates.</li>
     * <li>{@code kind}={@code script} with a {@code language}+{@code script} makes the node run a script
     * on the executor (shell/python/sql) instead of a bean method. It reads the inbound channels and
     * writes channel updates through the same {@code input.json}/{@code result.json} contract, so a
     * script node chains with bean and HTTP nodes identically.</li>
     * </ul>
     * All three kinds share the same channel input/output, at the same granularity as a bean method.
     * New fields are optional so older woven/stored definitions (seven fields) still deserialize.
     */
    public record NodeDef(String name, boolean entry, String trigger, int retries, String beanName,
            String methodName, String subgraph, String application, String kind,
            HttpEndpointDef endpoint, IoMappingDef io, String language, String script,
            String datasource) implements Serializable {

        /** Back-compatible constructor for a bean/script node hosted by the graph's own application. */
        public NodeDef(String name, boolean entry, String trigger, int retries, String beanName,
                String methodName, String subgraph) {
            this(name, entry, trigger, retries, beanName, methodName, subgraph, null, null, null, null,
                    null, null, null);
        }

        /** True when this node is an HTTP call the scheduler makes itself rather than a dispatched bean. */
        public boolean isHttp() {
            return "http".equalsIgnoreCase(kind) || endpoint != null;
        }

        /** True when this node runs a script on the executor rather than invoking a bean method. */
        public boolean isScript() {
            return "script".equalsIgnoreCase(kind)
                    || (language != null && !language.isBlank() && script != null && !script.isBlank());
        }
    }

    /** Where and how an HTTP node is called. {@code method} defaults to POST when blank. */
    public record HttpEndpointDef(String url, String method, Map<String, String> headers)
            implements Serializable {}

    /**
     * Optional channel wiring for an HTTP node. {@code input} maps a request field name to the channel
     * to read (absent ⇒ send the whole merged state as the JSON body / query). {@code output} maps a
     * channel to write from a field of the JSON response, by dotted path (absent ⇒ the response object's
     * own keys are the channel updates).
     */
    public record IoMappingDef(Map<String, String> input, Map<String, String> output)
            implements Serializable {}

    public record EdgeDef(String from, String to, String condition, String branch)
            implements Serializable {}

    public record ConditionalDef(List<String> sources, String form, String expression,
            List<String> predicates, Map<String, List<String>> branches, List<String> elseTargets)
            implements Serializable {}

    /** A dynamic fan-out (sharded) node: split {@code input}, dispatch shards across the cluster to the
     *  node's bean+method, gather the partials into {@code output}. */
    public record ShardDef(String node, String input, String output, int size)
            implements Serializable {}

}
