package com.github.cronsmith.springapp.example;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.github.cronflow.springapp.executor.Channel;
import com.github.cronflow.springapp.executor.Dag;
import com.github.cronflow.springapp.executor.DagNode;
import com.github.cronflow.springapp.executor.DagState;

/**
 * The nested workflow run as the {@code fulfilment} node of {@link OrderFlow}. It is an ordinary
 * {@code @Dag} — a graph on its own — with no {@code @Task}, so it is only ever run as a subgraph.
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
        channels = {@Channel(name = "steps", reducer = "concatList")})
@Component
public class FulfilmentFlow {

    @DagNode(entry = true, to = {"packBox", "printLabel"})
    public Map<String, Object> pickStock(DagState state) {
        return Map.of("steps", List.of("picked"));
    }

    @DagNode(to = {"handToCourier"})
    public Map<String, Object> packBox(DagState state) {
        return Map.of("steps", List.of("packed"));
    }

    @DagNode(to = {"handToCourier"})
    public Map<String, Object> printLabel(DagState state) {
        return Map.of("steps", List.of("labelled"));
    }

    // Fan-in: default Trigger ALL waits for both packBox and printLabel.
    @DagNode
    public Map<String, Object> handToCourier(DagState state) {
        return Map.of("steps", List.of("collected"));
    }

}
