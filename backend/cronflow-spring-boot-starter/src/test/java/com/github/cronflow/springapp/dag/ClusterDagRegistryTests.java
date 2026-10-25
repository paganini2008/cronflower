package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronflow.springapp.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.pojo.DagSyncMessage;

/**
 * Covers the gossip replication of the DAG registry: local apply + propagate as leader (multicast) and
 * as follower (forward to leader), startup wiring, and the inbound payload handler.
 */
class ClusterDagRegistryTests {

    private final DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
    private final GossipCluster cluster = mock(GossipCluster.class);
    private final ObjectCodec codec = mock(ObjectCodec.class);
    private final ClusterDagRegistry clusterRegistry =
            new ClusterDagRegistry(registry, cluster, codec);

    private DagRegistrationRequest reg() {
        return new DagRegistrationRequest("app", "i1", "run", "health", List.of(), List.of(), 1);
    }

    @Test
    void startupAddsListenerAndHydrates() {
        clusterRegistry.onApplicationEvent(null);
        verify(cluster).addListener(eq(ClusterDagRegistry.CHANNEL), eq(clusterRegistry));
        verify(registry).hydrateFromStore();
    }

    @Test
    void registerAsLeaderMulticasts() {
        when(registry.register(any())).thenReturn("assigned");
        when(codec.encode(any())).thenReturn(new byte[] {1});
        when(cluster.isLeader()).thenReturn(true);
        String id = clusterRegistry.register(reg());
        assertThat(id).isEqualTo("assigned");
        verify(cluster).multicastOn(eq(ClusterDagRegistry.CHANNEL), any(), any(), eq(false));
        verify(cluster, never()).sendToLeaderOn(anyString(), any());
    }

    @Test
    void registerAsFollowerForwardsToLeader() {
        when(registry.register(any())).thenReturn("assigned");
        when(codec.encode(any())).thenReturn(new byte[] {1});
        when(cluster.isLeader()).thenReturn(false);
        clusterRegistry.register(reg());
        verify(cluster).sendToLeaderOn(eq(ClusterDagRegistry.CHANNEL), any());
    }

    @Test
    void heartbeatPropagatesAndReturnsKnown() {
        when(registry.heartbeat(any())).thenReturn(true);
        when(codec.encode(any())).thenReturn(new byte[] {1});
        when(cluster.isLeader()).thenReturn(true);
        assertThat(clusterRegistry.heartbeat(new DagHeartbeatRequest("app", "i1", "run", "h", 1)))
                .isTrue();
        verify(cluster).multicastOn(eq(ClusterDagRegistry.CHANNEL), any(), any(), eq(false));
    }

    @Test
    void onPayloadAppliesRegistrationAndLeaderFansOut() {
        byte[] content = new byte[] {9};
        when(codec.decode(eq(content), eq(DagSyncMessage.class)))
                .thenReturn(DagSyncMessage.register(reg()));
        when(cluster.isLeader()).thenReturn(true);
        clusterRegistry.onPayload(mock(Node.class), content);
        verify(registry).register(any());
        verify(cluster).multicastOn(eq(ClusterDagRegistry.CHANNEL), any(), eq(content), eq(false));
    }

    @Test
    void onPayloadAppliesHeartbeatWithoutFanOutWhenNotLeader() {
        byte[] content = new byte[] {7};
        when(codec.decode(eq(content), eq(DagSyncMessage.class))).thenReturn(
                DagSyncMessage.heartbeat(new DagHeartbeatRequest("app", "i1", "run", "h", 1)));
        when(cluster.isLeader()).thenReturn(false);
        clusterRegistry.onPayload(mock(Node.class), content);
        verify(registry).heartbeat(any());
        verify(cluster, never()).multicastOn(anyString(), any(), any(), eq(false));
    }
}
