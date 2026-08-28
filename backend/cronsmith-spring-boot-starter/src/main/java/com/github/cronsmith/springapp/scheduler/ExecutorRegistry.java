package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory list of live executors, keyed by application. Not persisted: it is rebuilt from
 * registrations and heartbeats, and (in a cluster) kept in step across nodes by multicast.
 *
 * @Description: ExecutorRegistry
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class ExecutorRegistry {

    /** One reachable executor instance. Serializable so it can be multicast to other nodes. */
    public record ExecutorInstance(String application, String instanceId, String runUrl,
            String healthCheckUrl, long lastSeen, int weight) implements Serializable {
    }

    private final Map<String, Map<String, ExecutorInstance>> byApplication = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final ExecutorRouter router;

    public ExecutorRegistry(long ttlMillis) {
        this(ttlMillis, RoutingStrategy.ROUND_ROBIN);
    }

    public ExecutorRegistry(long ttlMillis, RoutingStrategy strategy) {
        this.ttlMillis = ttlMillis;
        this.router = new ExecutorRouter(strategy);
    }

    public RoutingStrategy routing() {
        return router.strategy();
    }

    /** Add or refresh an executor instance (weight &lt; 1 is treated as 1). */
    public void upsert(String application, String instanceId, String runUrl, String healthCheckUrl,
            int weight) {
        byApplication.computeIfAbsent(application, k -> new ConcurrentHashMap<>()).put(instanceId,
                new ExecutorInstance(application, instanceId, runUrl, healthCheckUrl,
                        System.currentTimeMillis(), Math.max(1, weight)));
    }

    /** Apply an instance received from another node (keeps the sender's lastSeen). */
    public void apply(ExecutorInstance instance) {
        byApplication.computeIfAbsent(instance.application(), k -> new ConcurrentHashMap<>())
                .put(instance.instanceId(), instance);
    }

    public void remove(String application, String instanceId) {
        Map<String, ExecutorInstance> instances = byApplication.get(application);
        if (instances != null) {
            instances.remove(instanceId);
        }
    }

    /** Pick a live executor of an application using the configured {@link RoutingStrategy}. */
    public Optional<ExecutorInstance> pick(String application, String routingKey) {
        Map<String, ExecutorInstance> instances = byApplication.get(application);
        if (instances == null || instances.isEmpty()) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        List<ExecutorInstance> live = new ArrayList<>();
        for (ExecutorInstance instance : instances.values()) {
            if (now - instance.lastSeen() <= ttlMillis) {
                live.add(instance);
            }
        }
        // Stable order, so FIRST/LAST/round-robin are deterministic across calls.
        live.sort((a, b) -> a.instanceId().compareTo(b.instanceId()));
        return router.choose(live, routingKey);
    }

    /** Drop instances that have not been heard from within the TTL. */
    public void evictStale() {
        long now = System.currentTimeMillis();
        byApplication.values()
                .forEach(instances -> instances.values()
                        .removeIf(instance -> now - instance.lastSeen() > ttlMillis));
    }

    /** A flat snapshot of every known instance. */
    public List<ExecutorInstance> snapshot() {
        List<ExecutorInstance> all = new ArrayList<>();
        byApplication.values().forEach(instances -> all.addAll(instances.values()));
        return all;
    }

}
