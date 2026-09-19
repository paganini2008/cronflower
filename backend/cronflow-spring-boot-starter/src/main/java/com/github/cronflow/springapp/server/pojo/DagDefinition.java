package com.github.cronflow.springapp.server.pojo;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Server-side copy of the woven DAG description received from an executor and stored in
 * {@code cf_task_dag}. Same schema as the executor's {@code DagDefinition} (deliberately duplicated
 * across modules, as cronsmith duplicates its request DTOs).
 *
 * @Description: DagDefinition
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagDefinition(String graph, List<String> inputs, List<ChannelDef> channels,
        List<NodeDef> nodes, List<EdgeDef> edges, List<ConditionalDef> conditionals,
        List<ShardDef> shards)
        implements Serializable {

    public record ChannelDef(String name, String reducer) implements Serializable {}

    public record NodeDef(String name, boolean entry, String trigger, int retries, String beanName,
            String methodName, String subgraph) implements Serializable {}

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
