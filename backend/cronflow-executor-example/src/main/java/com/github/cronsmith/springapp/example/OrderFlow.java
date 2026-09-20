package com.github.cronsmith.springapp.example;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.github.cronflow.springapp.executor.Channel;
import com.github.cronflow.springapp.executor.ChannelReducer;
import com.github.cronflow.springapp.executor.Dag;
import com.github.cronflow.springapp.executor.DagNode;
import com.github.cronflow.springapp.executor.DagState;
import com.github.cronflow.springapp.executor.When;
import com.github.cronsmith.springapp.executor.Task;

/**
 * Complex demo DAG (cronflow), modelled on the openspreader OrderFlowBestPractice:
 *
 * <pre>
 *                 validate
 *                 /      \            fan-out (parallel)
 *            reserve     charge
 *                 \      /            fan-in (Trigger ALL)
 *               riskScore
 *              /          \           conditional (SpEL on #risk)
 *   #risk>80  /            \  otherwise
 *      humanReview       fulfilment   <-- SUBGRAPH: runs "fulfilment-flow"
 *              \          /
 *               notify               any-of (Trigger ANY)
 * </pre>
 *
 * The cronsmith @Task {@link #kickoff()} triggers the whole thing; its return value is the DAG's
 * initial input. With amount = 500 the risk is low, so the {@code fulfilment} subgraph runs.
 */
@Dag(name = "order-flow", inputs = {"input"},
        channels = {@Channel(name = "steps", reducer = ChannelReducer.CONCAT_LIST)})
@Component
public class OrderFlow {

    /** Triggers the DAG every 30s; its return value ("order-123") is the DAG's initial input. */
    @Task(cron = "0/30 * * * * ?", description = "kick off the order-flow DAG")
    public String kickoff() {
        return "order-123";
    }

    @DagNode(entry = true, to = {"reserve", "charge"})
    public Map<String, Object> validate(DagState state) {
        pause();
        return Map.of("steps", List.of("validated:" + state.getString("input")), "amount", 500L);
    }

    @DagNode(to = {"riskScore"})
    public Map<String, Object> reserve(DagState state) {
        pause();
        return Map.of("steps", List.of("reserved"));
    }

    @DagNode(to = {"riskScore"})
    public Map<String, Object> charge(DagState state) {
        pause();
        return Map.of("steps", List.of("charged:" + state.getLong("amount")));
    }

    // Fan-in (Trigger ALL waits for reserve+charge) AND the conditional source: exactly one of
    // humanReview / fulfilment runs, the other is skipped.
    @DagNode(when = @When(expr = "#risk > 80", to = "humanReview"), otherwise = {"fulfilment"})
    public Map<String, Object> riskScore(DagState state) {
        pause();
        long risk = state.getLong("amount") > 1000 ? 90 : 10;
        return Map.of("risk", risk, "steps", List.of("scored:risk=" + risk));
    }

    @DagNode(to = {"notify"})
    public Map<String, Object> humanReview(DagState state) {
        pause();
        return Map.of("steps", List.of("queued-for-review"));
    }

    // A node that is itself a graph: runs the nested "fulfilment-flow" on the server.
    @DagNode(subgraph = "fulfilment-flow", to = {"notify"})
    public Map<String, Object> fulfilment(DagState state) {
        return Map.of();
    }

    // Any-of: whichever branch ran, notify once.
    @DagNode(trigger = "ANY")
    public Map<String, Object> notify(DagState state) {
        pause();
        return Map.of("steps", List.of("notified"));
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
