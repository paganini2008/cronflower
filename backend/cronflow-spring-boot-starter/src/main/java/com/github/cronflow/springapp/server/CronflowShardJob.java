package com.github.cronflow.springapp.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chaconneai.openspreader.aggregation.Emitter;
import com.chaconneai.openspreader.aggregation.MapReduceJob;
import com.github.cronflow.springapp.server.pojo.DagDefinition;
import com.github.cronflow.springapp.server.pojo.ShardPayload;

/**
 * The one MapReduce job every cronflow sharded node runs on. openspreader's aggregation splits the
 * work into shards and spreads them across the scheduler cluster; on each node this job's {@link #map}
 * dispatches its shard to the executor over HTTP (reusing {@link DagNodeDispatcher}), so a fan-out's
 * per-shard business logic still runs in the executor — the cluster only carries the scatter/gather.
 *
 * <p>
 * It is registered as a bean named {@code cronflowShardJob} and submitted by that name from
 * {@link CronflowShardedNode}. The shard's target bean/method and output channel travel in the
 * {@link ShardPayload}, so one job serves every sharded node.
 *
 * @Description: CronflowShardJob
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class CronflowShardJob implements MapReduceJob<ShardPayload, String, Object, Object> {

    /** The channel each shard's items arrive on for the executor's per-shard handler to read. */
    public static final String SHARD_CHANNEL = "__shard__";

    private final DagNodeDispatcher dispatcher;

    public CronflowShardJob(DagNodeDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Chop the items into shards: {@code size} items each when set, else one shard per available node. */
    @Override
    public List<ShardPayload> split(ShardPayload input, int suggestedShards) {
        List<Object> items = input.items() == null ? List.of() : input.items();
        int nodes = Math.max(1, suggestedShards);
        int shardSize = input.size() > 0 ? input.size()
                : Math.max(1, (int) Math.ceil((double) items.size() / nodes));
        List<ShardPayload> shards = new ArrayList<>();
        for (int i = 0; i < items.size(); i += shardSize) {
            List<Object> chunk = new ArrayList<>(items.subList(i, Math.min(i + shardSize, items.size())));
            shards.add(new ShardPayload(input.graph(), input.bean(), input.method(), input.output(),
                    chunk, input.size()));
        }
        return shards;
    }

    /** Run one shard: dispatch it to the executor bean+method, emit its partial under the output key. */
    @Override
    public void map(ShardPayload shard, Emitter<String, Object> emitter) {
        DagDefinition.NodeDef node = new DagDefinition.NodeDef("shard:" + shard.method(), false, "ALL",
                0, shard.bean(), shard.method(), "");
        Map<String, Object> state = Map.of(SHARD_CHANNEL, shard.items());
        DagNodeDispatcher.Dispatched dispatched = dispatcher.dispatch(shard.graph(), node, state);
        if (!dispatched.result().ok()) {
            throw new IllegalStateException("cronflow shard for '" + shard.method() + "' failed: "
                    + dispatched.result().errorDetail());
        }
        Map<String, Object> updates = dispatched.result().updates();
        Object partial = updates == null ? null : updates.get(shard.output());
        emitter.emit(shard.output(), partial);
    }

    /** Gather the shard partials: lists are concatenated, maps merged, otherwise collected into a list. */
    @Override
    @SuppressWarnings("unchecked")
    public Object reduce(String key, List<Object> values) {
        boolean allLists = !values.isEmpty();
        boolean allMaps = !values.isEmpty();
        for (Object v : values) {
            allLists &= v instanceof List;
            allMaps &= v instanceof Map;
        }
        if (allLists) {
            List<Object> merged = new ArrayList<>();
            for (Object v : values) {
                merged.addAll((List<Object>) v);
            }
            return merged;
        }
        if (allMaps) {
            Map<Object, Object> merged = new LinkedHashMap<>();
            for (Object v : values) {
                merged.putAll((Map<Object, Object>) v);
            }
            return merged;
        }
        return new ArrayList<>(values);
    }

}
