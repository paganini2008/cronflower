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

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.github.cronflow.springapp.executor.Channel;
import com.github.cronflow.springapp.executor.ChannelReducer;
import com.github.cronflow.springapp.executor.Dag;
import com.github.cronflow.springapp.executor.DagNode;
import com.github.cronflow.springapp.executor.DagState;
import com.github.cronsmith.springapp.executor.Task;

/**
 * A pure fan-out / fan-in {@code @Dag} — a graph on its own. It doubles as the nested {@code fulfilment}
 * subgraph of {@link OrderFlow}, and (via {@link #kickoff()}) as a standalone DAG the engine skeleton
 * can drive directly: no conditional, no subgraph, plain on-success edges, Trigger ALL fan-in.
 *
 * <pre>
 *              pickStock
 *              /       \        fan-out
 *          packBox   printLabel
 *              \       /        fan-in (Trigger ALL)
 *           handToCourier
 * </pre>
 */
@Dag(name = "fulfilment-flow",
        channels = {@Channel(name = "steps", reducer = ChannelReducer.CONCAT_LIST)})
@Component
public class FulfilmentFlow {

    /**
     * Triggers fulfilment-flow every 30s so the openspreader engine skeleton can be exercised end to
     * end on H2: the task seeds the initial {@code steps}, the DAG then flows across the scheduler
     * cluster while each node's method runs here in the executor over HTTP.
     */
    @Task(cron = "0/30 * * * * ?", description = "kick off the fulfilment-flow DAG")
    public Map<String, Object> kickoff() {
        return Map.of("steps", List.of("kickoff"));
    }

    @DagNode(entry = true, to = {"packBox", "printLabel"})
    public Map<String, Object> pickStock(DagState state) {
        pause();
        return Map.of("steps", List.of("picked"));
    }

    @DagNode(to = {"handToCourier"})
    public Map<String, Object> packBox(DagState state) {
        pause();
        return Map.of("steps", List.of("packed"));
    }

    @DagNode(to = {"handToCourier"})
    public Map<String, Object> printLabel(DagState state) {
        pause();
        return Map.of("steps", List.of("labelled"));
    }

    // Fan-in: default Trigger ALL waits for both packBox and printLabel.
    @DagNode
    public Map<String, Object> handToCourier(DagState state) {
        pause();
        return Map.of("steps", List.of("collected"));
    }

    /** A little simulated work so a run lasts a few seconds and the console can show the live node
     *  pulsing as the flow moves through the graph. */
    private void pause() {
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
