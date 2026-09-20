package com.github.cronflow.springapp.server;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.cronflow.springapp.server.pojo.DagDefinition;
import com.github.cronflow.springapp.server.pojo.DagGraph;
import com.github.cronflow.springapp.server.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.server.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.server.pojo.StoredDag;
import com.github.cronflow.springapp.server.pojo.TriggerBinding;

/**
 * In-memory registry of cronflow executor instances and the graphs they host, plus the task→DAG
 * trigger bindings — the cronflow counterpart of cronsmith's {@code ExecutorRegistry}. Definitions are
 * also persisted to {@code cf_task_dag} via {@link DagStore} so they survive a restart; the parsed
 * definitions are kept here for the coordinator to run.
 *
 * <p>
 * TODO: reuse cronsmith's {@code ExecutorRouter}/{@code RoutingStrategy} instead of the plain
 * round-robin below; warm the registry from {@link DagStore#loadAll()} on startup.
 *
 * @Description: DagExecutorRegistry
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class DagExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(DagExecutorRegistry.class);

    /** One executor instance able to run DAG nodes. */
    public record ExecutorInstance(String application, String instanceId, String runUrl,
            String healthCheckUrl, int weight, Instant lastSeen) {}

    private final DagStore dagStore;
    private final DagDefinitionCodec codec;
    private final String definitionFormat;
    private final long ttlSeconds;

    private final Map<String, ExecutorInstance> instances = new ConcurrentHashMap<>();
    /** graph name -> the instance ids that host it. */
    private final Map<String, List<String>> hostsByGraph = new ConcurrentHashMap<>();
    /** graph name -> its parsed definition. */
    private final Map<String, DagDefinition> definitions = new ConcurrentHashMap<>();
    /** graph name -> the application that registered it (for the run log). */
    private final Map<String, String> applicationByGraph = new ConcurrentHashMap<>();
    /** taskGroup + "/" + taskName -> graph name. */
    private final Map<String, String> triggerBindings = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobin = new AtomicInteger();

    public DagExecutorRegistry(DagStore dagStore, DagDefinitionCodec codec, String definitionFormat,
            long ttlSeconds) {
        this.dagStore = dagStore;
        this.codec = codec;
        this.definitionFormat = codec.normalize(definitionFormat);
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Warm the in-memory definition cache from the persisted store ({@code cf_task_dag}) — called on
     * startup so the console's DAG list survives a full server-cluster restart without waiting for
     * executors to re-register. Only definitions are restored (they are persistable); the live host
     * mapping ({@code hostsByGraph}) still needs a live executor to run a graph, and is rebuilt when the
     * executor re-registers.
     */
    public int hydrateFromStore() {
        int loaded = 0;
        try {
            for (StoredDag stored : dagStore.loadAll()) {
                try {
                    DagDefinition parsed =
                            codec.decode(stored.definition(), codec.normalize(stored.format()));
                    definitions.put(parsed.graph(), parsed);
                    applicationByGraph.put(parsed.graph(), stored.application());
                    loaded++;
                } catch (RuntimeException e) {
                    log.warn("cronflow: could not decode stored graph {}: {}", stored.graph(),
                            e.toString());
                }
            }
        } catch (RuntimeException e) {
            log.warn("cronflow: could not warm DAG registry from store: {}", e.toString());
        }
        if (loaded > 0) {
            log.info("cronflow: warmed {} DAG definition(s) from the store on startup", loaded);
        }
        return loaded;
    }

    /** Record a full registration: instance, its graphs (persisted) and its trigger bindings. */
    public String register(DagRegistrationRequest request) {
        String instanceId = request.instanceId() != null && !request.instanceId().isBlank()
                ? request.instanceId()
                : java.util.UUID.randomUUID().toString();
        instances.put(instanceId, new ExecutorInstance(request.application(), instanceId,
                request.runUrl(), request.healthCheckUrl(),
                request.weight() == null ? 1 : request.weight(), Instant.now()));

        if (request.dags() != null) {
            for (DagDefinition dag : request.dags()) {
                definitions.put(dag.graph(), dag);
                applicationByGraph.put(dag.graph(), request.application());
                hostsByGraph.computeIfAbsent(dag.graph(), g -> new ArrayList<>());
                synchronized (hostsByGraph.get(dag.graph())) {
                    List<String> hosts = hostsByGraph.get(dag.graph());
                    if (!hosts.contains(instanceId)) {
                        hosts.add(instanceId);
                    }
                }
                persist(request.application(), dag);
            }
        }
        if (request.triggers() != null) {
            for (TriggerBinding b : request.triggers()) {
                triggerBindings.put(b.taskGroup() + "/" + b.taskName(), b.graph());
            }
        }
        log.info("cronflow: registered executor {} ({}), {} graph(s), {} trigger(s)", instanceId,
                request.runUrl(), request.dags() == null ? 0 : request.dags().size(),
                request.triggers() == null ? 0 : request.triggers().size());
        return instanceId;
    }

    /**
     * Refresh a known instance's liveness. Returns {@code false} when this instance is unknown here —
     * e.g. the whole server cluster restarted and lost its in-memory registry — so the executor knows
     * to re-register its DAG definitions (a heartbeat carries none) instead of heartbeating into a void.
     */
    public boolean heartbeat(DagHeartbeatRequest request) {
        ExecutorInstance existing = instances.get(request.instanceId());
        if (existing == null) {
            return false;
        }
        instances.put(request.instanceId(), new ExecutorInstance(existing.application(),
                existing.instanceId(), request.runUrl(), request.healthCheckUrl(),
                request.weight() == null ? existing.weight() : request.weight(), Instant.now()));
        return true;
    }

    private void persist(String application, DagDefinition dag) {
        try {
            dagStore.save(application, dag.graph(), codec.encode(dag, definitionFormat), definitionFormat);
        } catch (Exception e) {
            log.warn("cronflow: failed to persist graph {}: {}", dag.graph(), e.toString());
        }
    }

    /**
     * The definition for a graph: from the in-memory cache, else loaded from {@code cf_task_dag} (so a
     * node that did not receive the registration — e.g. the leader in a shared-store cluster — can still
     * run it). Executor discovery ({@link #pick}) still needs the instance to be known on this node.
     */
    public DagDefinition definition(String graph) {
        DagDefinition cached = definitions.get(graph);
        if (cached != null) {
            return cached;
        }
        try {
            for (StoredDag stored : dagStore.loadAll()) {
                if (graph.equals(stored.graph())) {
                    DagDefinition parsed = codec.decode(stored.definition(),
                            codec.normalize(stored.format()));
                    definitions.put(graph, parsed);
                    applicationByGraph.put(graph, stored.application());
                    return parsed;
                }
            }
        } catch (RuntimeException e) {
            log.debug("cronflow: store lookup for graph {} failed: {}", graph, e.toString());
        }
        return null;
    }

    /** The application that owns a graph (for the run log), if known. */
    public Optional<String> applicationOf(String graph) {
        return Optional.ofNullable(applicationByGraph.get(graph));
    }

    /** Every known graph with its owning application and parsed definition, for the console. The
     *  in-memory map is gossip-replicated ({@link ClusterDagRegistry}), so it is cluster-complete. */
    public List<DagGraph> graphs() {
        List<DagGraph> out = new ArrayList<>();
        for (Map.Entry<String, DagDefinition> e : definitions.entrySet()) {
            out.add(new DagGraph(applicationByGraph.getOrDefault(e.getKey(), ""), e.getValue()));
        }
        out.sort((a, b) -> a.definition().graph().compareToIgnoreCase(b.definition().graph()));
        return out;
    }

    public Optional<String> triggeredGraph(String taskGroup, String taskName) {
        return Optional.ofNullable(triggerBindings.get(taskGroup + "/" + taskName));
    }

    /** Distinct applications that currently have at least one live executor — the pool the console
     *  offers when authoring a DAG (its nodes' beans must live in one of these executors). */
    public List<String> liveApplications() {
        return instances.values().stream().filter(this::isLive)
                .map(ExecutorInstance::application).distinct().sorted().collect(Collectors.toList());
    }

    /** Any live executor instance of an application — used to host a console-authored graph so its
     *  nodes dispatch to that executor (where their beans/methods live). */
    public Optional<ExecutorInstance> anyLiveInstance(String application) {
        return instances.values().stream()
                .filter(i -> application.equals(i.application()) && isLive(i)).findFirst();
    }

    /** Pick a live instance hosting {@code graph}, round-robin. TODO: reuse cronsmith's router. */
    public Optional<ExecutorInstance> pick(String graph) {
        List<String> hosts = hostsByGraph.getOrDefault(graph, List.of());
        List<ExecutorInstance> live = hosts.stream().map(instances::get).filter(this::isLive)
                .collect(Collectors.toList());
        if (live.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(live.get(Math.floorMod(roundRobin.getAndIncrement(), live.size())));
    }

    private boolean isLive(ExecutorInstance instance) {
        return instance != null
                && Duration.between(instance.lastSeen(), Instant.now()).getSeconds() <= ttlSeconds;
    }

}
