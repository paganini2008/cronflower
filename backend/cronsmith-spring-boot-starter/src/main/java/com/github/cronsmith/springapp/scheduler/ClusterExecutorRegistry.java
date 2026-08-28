package com.github.cronsmith.springapp.scheduler;

import com.chaconneai.openspreader.cluster.SelfRegisteringListener;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.GossipListener;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry.ExecutorInstance;

/**
 * Keeps the in-memory {@link ExecutorRegistry} in step across the cluster. A change is applied
 * locally at once; a follower forwards it to the leader, and the leader multicasts it so every node
 * converges. Registration writes flow through the leader, exactly like task writes.
 *
 * @Description: ClusterExecutorRegistry
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class ClusterExecutorRegistry implements GossipListener, SelfRegisteringListener {

    public static final String CHANNEL = "cronsmith.executors";

    private final ExecutorRegistry registry;
    private final GossipCluster cluster;
    private final ObjectCodec codec;

    public ClusterExecutorRegistry(ExecutorRegistry registry, GossipCluster cluster,
            ObjectCodec codec) {
        this.registry = registry;
        this.cluster = cluster;
        this.codec = codec;
    }

    public void start() {
        cluster.addListener(CHANNEL, this);
    }

    /** Add or refresh an executor (from a register or heartbeat), and propagate it. */
    public void register(String application, String instanceId, String runUrl, String healthCheckUrl,
            int weight) {
        ExecutorInstance instance = new ExecutorInstance(application, instanceId, runUrl,
                healthCheckUrl, System.currentTimeMillis(), Math.max(1, weight));
        registry.apply(instance);
        propagate(ExecutorSyncMessage.upsert(instance));
    }

    /** Drop an executor (a failed health check), and propagate the removal. */
    public void remove(ExecutorInstance instance) {
        registry.remove(instance.application(), instance.instanceId());
        propagate(ExecutorSyncMessage.remove(instance));
    }

    private void propagate(ExecutorSyncMessage message) {
        byte[] payload = codec.encode(message);
        if (cluster.isLeader()) {
            cluster.multicastOn(CHANNEL, null, payload, false);
        } else {
            cluster.sendToLeaderOn(CHANNEL, payload);
        }
    }

    @Override
    public void onPayload(Node sender, byte[] content) {
        ExecutorSyncMessage message = (ExecutorSyncMessage) codec.decode(content,
                ExecutorSyncMessage.class);
        apply(message);
        // A follower forwarded this to us; as leader, fan it out to the rest.
        if (cluster.isLeader()) {
            cluster.multicastOn(CHANNEL, null, content, false);
        }
    }

    private void apply(ExecutorSyncMessage message) {
        ExecutorInstance instance = message.instance();
        if (message.op() == ExecutorSyncMessage.Op.UPSERT) {
            registry.apply(instance);
        } else {
            registry.remove(instance.application(), instance.instanceId());
        }
    }

}
