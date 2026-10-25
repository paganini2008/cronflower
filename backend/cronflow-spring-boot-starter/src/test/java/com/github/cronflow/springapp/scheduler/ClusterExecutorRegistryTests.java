package com.github.cronflow.springapp.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronflow.springapp.scheduler.ExecutorRegistry.ExecutorInstance;

/**
 * Covers the gossip replication of the executor registry: local apply + leader multicast / follower
 * forward, removal, startup listening, and the inbound payload handler.
 */
class ClusterExecutorRegistryTests {

    private final ExecutorRegistry registry = mock(ExecutorRegistry.class);
    private final GossipCluster cluster = mock(GossipCluster.class);
    private final ObjectCodec codec = mock(ObjectCodec.class);
    private final ClusterExecutorRegistry clusterRegistry =
            new ClusterExecutorRegistry(registry, cluster, codec);

    private ExecutorInstance instance() {
        return new ExecutorInstance("app", "i1", "run", "health", System.currentTimeMillis(), 1);
    }

    @Test
    void startAddsListener() {
        clusterRegistry.start();
        verify(cluster).addListener(eq(ClusterExecutorRegistry.CHANNEL), eq(clusterRegistry));
    }

    @Test
    void registerAppliesLocallyAndMulticastsAsLeader() {
        when(codec.encode(any())).thenReturn(new byte[] {1});
        when(cluster.isLeader()).thenReturn(true);
        clusterRegistry.register("app", "i1", "run", "health", 2);
        verify(registry).apply(any(ExecutorInstance.class));
        verify(cluster).multicastOn(eq(ClusterExecutorRegistry.CHANNEL), any(), any(), eq(false));
    }

    @Test
    void registerForwardsToLeaderAsFollower() {
        when(codec.encode(any())).thenReturn(new byte[] {1});
        when(cluster.isLeader()).thenReturn(false);
        clusterRegistry.register("app", "i1", "run", "health", 0);
        verify(cluster).sendToLeaderOn(eq(ClusterExecutorRegistry.CHANNEL), any());
    }

    @Test
    void removeAppliesAndPropagates() {
        when(codec.encode(any())).thenReturn(new byte[] {1});
        when(cluster.isLeader()).thenReturn(true);
        clusterRegistry.remove(instance());
        verify(registry).remove("app", "i1");
        verify(cluster).multicastOn(eq(ClusterExecutorRegistry.CHANNEL), any(), any(), eq(false));
    }

    @Test
    void onPayloadUpsertAppliesAndLeaderFansOut() {
        byte[] content = new byte[] {9};
        when(codec.decode(eq(content), eq(ExecutorSyncMessage.class)))
                .thenReturn(ExecutorSyncMessage.upsert(instance()));
        when(cluster.isLeader()).thenReturn(true);
        clusterRegistry.onPayload(mock(Node.class), content);
        verify(registry).apply(any(ExecutorInstance.class));
        verify(cluster).multicastOn(eq(ClusterExecutorRegistry.CHANNEL), any(), eq(content), eq(false));
    }

    @Test
    void onPayloadRemoveWithoutFanOutWhenFollower() {
        byte[] content = new byte[] {8};
        when(codec.decode(eq(content), eq(ExecutorSyncMessage.class)))
                .thenReturn(ExecutorSyncMessage.remove(instance()));
        when(cluster.isLeader()).thenReturn(false);
        clusterRegistry.onPayload(mock(Node.class), content);
        verify(registry).remove("app", "i1");
        verify(cluster, never()).multicastOn(anyString(), any(), any(), eq(false));
    }
}
