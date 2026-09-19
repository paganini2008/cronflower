package com.github.cronflow.springapp.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.chaconneai.openspreader.aggregation.MapReduceResult;
import com.chaconneai.openspreader.aggregation.ProcessingMapReduce;
import com.chaconneai.openspreader.dag.GraphNode;
import com.chaconneai.openspreader.dag.GraphState;
import com.chaconneai.openspreader.dag.NodeContext;
import com.github.cronflow.springapp.server.pojo.ShardPayload;

/**
 * The one node bean the engine dispatches for a {@code @DagNode(shard=...)} node: it reads the input
 * collection from the run state, submits it to openspreader's MapReduce (which splits it and spreads
 * the shards across the scheduler cluster, each shard dispatched to the executor by
 * {@link CronflowShardJob}), and writes the gathered result to the output channel. Config-driven, like
 * {@link CronflowNode}: the input/output channels and the shard's bean+method arrive in the
 * {@link NodeContext}.
 *
 * @Description: CronflowShardedNode
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class CronflowShardedNode extends GraphNode {

    private final ProcessingMapReduce mapReduce;

    public CronflowShardedNode(ProcessingMapReduce mapReduce) {
        this.mapReduce = mapReduce;
    }

    @Override
    public Map<String, Object> execute(GraphState state) {
        throw new IllegalStateException("cronflow sharded node needs its NodeContext (input/output/bean)");
    }

    @Override
    public Map<String, Object> execute(GraphState state, NodeContext context) {
        String input = context.getString("input");
        String output = context.getString("output");
        String bean = context.getString("bean");
        String method = context.getString("method");
        int size = (int) context.getLong("size", 0);

        Object collection = state.get(input);
        if (collection == null) {
            throw new IllegalStateException("sharded node '" + context.node() + "' reads channel '"
                    + input + "', which nothing has written");
        }
        List<Object> items = asList(collection);
        if (items.isEmpty()) {
            return Map.of(output, List.of()); // nothing to shard is not a failure
        }
        ShardPayload payload =
                new ShardPayload(context.graph(), bean, method, output, items, size);
        try {
            MapReduceResult<String, Object> result =
                    mapReduce.<ShardPayload, String, Object, Object>submit("cronflowShardJob", payload)
                            .get();
            Object merged = result.get(output);
            return merged == null ? Map.of() : Map.of(output, merged);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sharded node '" + context.node() + "' was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("sharded node '" + context.node() + "' failed: "
                    + cause.getMessage(), cause);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        if (value instanceof Collection) {
            return new ArrayList<>((Collection<Object>) value);
        }
        if (value.getClass().isArray()) {
            List<Object> out = new ArrayList<>();
            int n = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < n; i++) {
                out.add(java.lang.reflect.Array.get(value, i));
            }
            return out;
        }
        return List.of(value);
    }

}
