package com.github.cronflow.springapp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.GossipListener;
import com.github.cronflow.springapp.server.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.server.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.server.pojo.DagSyncMessage;

/**
 * Keeps the in-memory {@link DagExecutorRegistry} in step across the cluster, exactly like cronsmith's
 * {@code ClusterExecutorRegistry} keeps the executor list in step. A registration is applied locally
 * at once; a follower forwards it to the leader, and the leader multicasts it so every node converges.
 *
 * <p>
 * This is why cronflow inherits cronsmith's consistency: the DAG definitions, trigger bindings and
 * executor instances live on <b>every</b> node, so whichever node is leader (seed or not, before or
 * after a failover) can trigger and run a DAG. Each node also persists what it receives to its own
 * {@code cf_task_dag}, so node-local (per-node H2/SQLite) clusters replicate just like cronsmith.
 *
 * @Description: ClusterDagRegistry
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class ClusterDagRegistry
        implements GossipListener, ApplicationListener<ApplicationReadyEvent> {

    public static final String CHANNEL = "cronflow.dags";

    private static final Logger log = LoggerFactory.getLogger(ClusterDagRegistry.class);

    private final DagExecutorRegistry registry;
    private final GossipCluster cluster;
    private final ObjectCodec codec;

    public ClusterDagRegistry(DagExecutorRegistry registry, GossipCluster cluster, ObjectCodec codec) {
        this.registry = registry;
        this.cluster = cluster;
        this.codec = codec;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        cluster.addListener(CHANNEL, this);
        // Restore DAG definitions from the store so the console list survives a full cluster restart
        // without waiting for executors to re-register (definitions are persisted; live hosts are not).
        registry.hydrateFromStore();
        log.info("cronflow: cluster DAG registry listening on '{}'", CHANNEL);
    }

    /** Apply a registration locally (assigns the instance id), then propagate it to the cluster. */
    public String register(DagRegistrationRequest request) {
        String instanceId = registry.register(request);
        DagRegistrationRequest withId = new DagRegistrationRequest(request.application(), instanceId,
                request.runUrl(), request.healthCheckUrl(), request.dags(), request.triggers(),
                request.weight());
        propagate(DagSyncMessage.register(withId));
        return instanceId;
    }

    /** Apply a heartbeat locally, then propagate it. Returns whether this instance was known here (so
     *  the executor can re-register its definitions after a cluster restart wiped the registry). */
    public boolean heartbeat(DagHeartbeatRequest request) {
        boolean known = registry.heartbeat(request);
        propagate(DagSyncMessage.heartbeat(request));
        return known;
    }

    private void propagate(DagSyncMessage message) {
        byte[] payload = codec.encode(message);
        if (cluster.isLeader()) {
            cluster.multicastOn(CHANNEL, null, payload, false);
        } else {
            cluster.sendToLeaderOn(CHANNEL, payload);
        }
    }

    @Override
    public void onPayload(Node sender, byte[] content) {
        DagSyncMessage message = (DagSyncMessage) codec.decode(content, DagSyncMessage.class);
        apply(message);
        // A follower forwarded this to us; as leader, fan it out to the rest.
        if (cluster.isLeader()) {
            cluster.multicastOn(CHANNEL, null, content, false);
        }
    }

    private void apply(DagSyncMessage message) {
        if (message.registration() != null) {
            registry.register(message.registration());
        } else if (message.heartbeat() != null) {
            registry.heartbeat(message.heartbeat());
        }
    }

}
