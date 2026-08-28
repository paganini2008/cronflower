package com.github.cronsmith.springapp.scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.NodeState;
import com.chaconneai.spreader.loadbalance.LoadBalancer;
import com.chaconneai.spreader.loadbalance.WeightedLoadBalancer;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry.ExecutorInstance;

/**
 * Chooses one executor instance from the live candidates, per the configured {@link RoutingStrategy}.
 *
 * <p>
 * The load-spreading strategies reuse openspreader's {@code LoadBalancer} (round-robin, random,
 * consistent-hash, weighted) by adapting each {@link ExecutorInstance} to a {@code Node} — so the
 * behaviour, including the consistent-hash ring and smooth weighting, is exactly the cluster's. The
 * balancer is stateful, so hold one instance per registry.
 *
 * @Description: ExecutorRouter
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public class ExecutorRouter {

    private final RoutingStrategy strategy;
    private final LoadBalancer loadBalancer;

    public ExecutorRouter(RoutingStrategy strategy) {
        this.strategy = strategy != null ? strategy : RoutingStrategy.ROUND_ROBIN;
        this.loadBalancer = switch (this.strategy) {
            case ROUND_ROBIN -> LoadBalancer.roundRobin();
            case RANDOM -> LoadBalancer.random();
            case CONSISTENT_HASH -> LoadBalancer.consistentHash();
            case WEIGHTED -> LoadBalancer.weighted();
            case FIRST, LAST -> null;
        };
    }

    public RoutingStrategy strategy() {
        return strategy;
    }

    /**
     * @param live candidate executors (already filtered to the live ones), assumed non-empty
     * @param key  routing key for {@code CONSISTENT_HASH} (e.g. the task group); may be null
     */
    public Optional<ExecutorInstance> choose(List<ExecutorInstance> live, String key) {
        if (live.isEmpty()) {
            return Optional.empty();
        }
        if (live.size() == 1) {
            return Optional.of(live.get(0));
        }
        return switch (strategy) {
            case FIRST -> Optional.of(live.get(0));
            case LAST -> Optional.of(live.get(live.size() - 1));
            default -> byLoadBalancer(live, key);
        };
    }

    private Optional<ExecutorInstance> byLoadBalancer(List<ExecutorInstance> live, String key) {
        List<Node> nodes = new ArrayList<>(live.size());
        Map<String, ExecutorInstance> byId = new HashMap<>();
        for (ExecutorInstance e : live) {
            nodes.add(toNode(e));
            byId.put(e.instanceId(), e);
        }
        Node chosen = loadBalancer.choose(nodes, key);
        return chosen != null ? Optional.ofNullable(byId.get(chosen.id())) : Optional.of(live.get(0));
    }

    private static Node toNode(ExecutorInstance e) {
        Map<String, String> metadata =
                Map.of(WeightedLoadBalancer.WEIGHT_KEY, String.valueOf(Math.max(1, e.weight())));
        return new Node(e.instanceId(), e.application(), hostOf(e.instanceId()), 0, 0L, 0L,
                NodeState.ALIVE, 0, metadata);
    }

    private static String hostOf(String instanceId) {
        int i = instanceId.lastIndexOf(':');
        return i > 0 ? instanceId.substring(0, i) : instanceId;
    }
}
