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
package com.github.cronsmith.springapp.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.github.cronflow.springapp.executor.Channel;
import com.github.cronflow.springapp.executor.ChannelReducer;
import com.github.cronflow.springapp.executor.Dag;
import com.github.cronflow.springapp.executor.DagNode;
import com.github.cronflow.springapp.executor.DagState;
import com.github.cronflow.springapp.executor.Shard;
import com.github.cronsmith.springapp.executor.Task;

/**
 * A dynamic fan-out (sharded) demo. {@link #kickoff()} produces a list of ids as the DAG's initial
 * state; {@code square} is a {@code @DagNode(shard=...)} node — the engine splits the ids into shards,
 * spreads them across the scheduler cluster, dispatches each shard to this {@code square} method over
 * HTTP (its shard arrives on {@code Shard.CHANNEL}), and gathers the partial lists into {@code squares}.
 * {@code report} then logs the gathered result.
 *
 * <pre>
 *   kickoff (@Task) → generate ("ids") → square (sharded) → report
 * </pre>
 *
 * <p>
 * The structured id list is produced by an ordinary {@code generate} node (a node's returned map is
 * spread into channels), not by the {@code @Task} return — a task return crosses back as a scalar, so
 * a DAG that needs a structured seed makes it in a node.
 */
@Dag(name = "shard-flow", channels = {
        @Channel(name = "squares", reducer = ChannelReducer.CONCAT_LIST)})
@Component
public class ShardFlow {

    private static final Logger log = LoggerFactory.getLogger(ShardFlow.class);

    /** Triggers the DAG every 30s. */
    @Task(cron = "0/30 * * * * ?", description = "kick off the sharded shard-flow DAG")
    public String kickoff() {
        return "go";
    }

    /** Entry node: produces the list of ids to process in parallel shards. */
    @DagNode(entry = true, to = {"square"})
    public Map<String, Object> generate(DagState state) {
        pause();
        List<Object> ids = new ArrayList<>(IntStream.rangeClosed(1, 20).boxed().toList());
        return Map.of("ids", ids);
    }

    /** Per-shard handler: squares just this shard's ids and returns the partial list. */
    @DagNode(shard = @Shard(input = "ids", output = "squares"), to = {"report"})
    public Map<String, Object> square(DagState state) {
        pause();
        List<Object> shard = state.getList(Shard.CHANNEL);
        List<Object> out = new ArrayList<>();
        for (Object o : shard) {
            long v = ((Number) o).longValue();
            out.add(v * v);
        }
        log.info("cronflow: squared a shard of {} id(s) -> {}", shard.size(), out);
        return Map.of("squares", out);
    }

    /** Fan-in: log the gathered squares. */
    @DagNode
    public Map<String, Object> report(DagState state) {
        pause();
        List<Object> squares = state.getList("squares");
        log.info("cronflow: shard-flow gathered {} squares: {}", squares.size(), squares);
        return Map.of();
    }

    /** Simulated work so each node stays RUNNING long enough to see the live pulse on the graph. */
    private void pause() {
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
