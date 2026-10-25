package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronflow.springapp.pojo.DagNodeView;
import com.github.cronflow.springapp.pojo.DagRunLogMessage;
import com.github.cronflow.springapp.pojo.DagRunView;

/**
 * Covers the cluster run-log wrapper: writes are stored locally and replicated (leader multicast /
 * follower forward), reads delegate to the local store, the frontier is tracked and replicated, and
 * replication is skipped entirely for a shared store.
 */
class ClusterDagRunLogTests {

    private final DagRunLog store = mock(DagRunLog.class);
    private final GossipCluster cluster = mock(GossipCluster.class);
    private final ObjectCodec codec = mock(ObjectCodec.class);

    private ClusterDagRunLog replicating() {
        when(codec.encode(any())).thenReturn(new byte[] {1});
        return new ClusterDagRunLog(store, cluster, codec, true);
    }

    @Test
    void startupListensOnlyWhenReplicating() {
        replicating().onApplicationEvent(null);
        verify(cluster).addListener(eq(ClusterDagRunLog.CHANNEL), any());
        GossipCluster other = mock(GossipCluster.class);
        new ClusterDagRunLog(store, other, codec, false).onApplicationEvent(null);
        verify(other, never()).addListener(anyString(), any());
    }

    @Test
    void beginFinishNodeStoreThenReplicateAsLeader() {
        when(cluster.isLeader()).thenReturn(true);
        ClusterDagRunLog log = replicating();
        LocalDateTime now = LocalDateTime.now();
        log.begin("r", "p", "app", "g", "task", now, "{}");
        log.finish("r", "SUCCESS", now, 5L, 2, null, null, "{}");
        log.node("r", "g", "n", 0, "SUCCESS", "in", "out", "exec", 3L, null);
        verify(store).begin("r", "p", "app", "g", "task", now, "{}");
        verify(store).finish("r", "SUCCESS", now, 5L, 2, null, null, "{}");
        verify(store).node("r", "g", "n", 0, "SUCCESS", "in", "out", "exec", 3L, null);
        verify(cluster, org.mockito.Mockito.times(3)).multicastOn(eq(ClusterDagRunLog.CHANNEL), any(),
                any(), eq(false));
    }

    @Test
    void followerForwardsToLeader() {
        when(cluster.isLeader()).thenReturn(false);
        replicating().begin("r", null, "app", "g", "task", LocalDateTime.now(), "{}");
        verify(cluster).sendToLeaderOn(eq(ClusterDagRunLog.CHANNEL), any());
    }

    @Test
    void sharedStoreSkipsReplication() {
        ClusterDagRunLog log = new ClusterDagRunLog(store, cluster, codec, false);
        log.begin("r", null, "app", "g", "task", LocalDateTime.now(), "{}");
        log.node("r", "g", "n", 0, "SUCCESS", null, null, "exec", 1L, null);
        verify(store).begin(any(), any(), any(), any(), any(), any(), any());
        verify(cluster, never()).multicastOn(anyString(), any(), any(), eq(false));
        verify(cluster, never()).sendToLeaderOn(anyString(), any());
    }

    @Test
    void frontierIsTrackedAndCleared() {
        when(cluster.isLeader()).thenReturn(true);
        ClusterDagRunLog log = replicating();
        log.frontier("r", Set.of("n1", "n2"));
        assertThat(log.frontierOf("r")).containsExactlyInAnyOrder("n1", "n2");
        log.frontier("r", null); // null normalises to empty
        assertThat(log.frontierOf("r")).isEmpty();
        log.clearFrontier("r");
        assertThat(log.frontierOf("unknown")).isEmpty();
    }

    @Test
    void propagateSwallowsEncodeFailure() {
        when(cluster.isLeader()).thenReturn(true);
        when(codec.encode(any())).thenThrow(new RuntimeException("encode boom"));
        ClusterDagRunLog log = new ClusterDagRunLog(store, cluster, codec, true);
        // begin stores locally and the replication failure is swallowed
        log.begin("r", null, "app", "g", "task", LocalDateTime.now(), "{}");
        verify(store).begin(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void readsDelegateToStore() {
        ClusterDagRunLog log = new ClusterDagRunLog(store, cluster, codec, false);
        DagRunView view = new DagRunView("r", null, "app", "g", "t", "SUCCESS", null, null, null, null,
                null, null, null, null);
        when(store.listRuns("app", "g", "SUCCESS", 10, 0)).thenReturn(List.of(view));
        when(store.countRuns("app", "g", "SUCCESS")).thenReturn(1);
        when(store.findRun("r")).thenReturn(view);
        when(store.nodesOf("r")).thenReturn(List.of());
        when(store.childRuns("r")).thenReturn(List.of(view));
        assertThat(log.listRuns("app", "g", "SUCCESS", 10, 0)).containsExactly(view);
        assertThat(log.countRuns("app", "g", "SUCCESS")).isEqualTo(1);
        assertThat(log.findRun("r")).isSameAs(view);
        assertThat(log.nodesOf("r")).isEmpty();
        assertThat(log.childRuns("r")).containsExactly(view);
    }

    @Test
    void onPayloadAppliesEachOpToStore() {
        when(cluster.isLeader()).thenReturn(false);
        ClusterDagRunLog log = new ClusterDagRunLog(store, cluster, codec, true);
        LocalDateTime now = LocalDateTime.now();
        decodeReturns(DagRunLogMessage.begin("r", "p", "app", "g", "t", now, "{}"));
        log.onPayload(mock(Node.class), new byte[] {1});
        verify(store).begin("r", "p", "app", "g", "t", now, "{}");

        decodeReturns(DagRunLogMessage.finish("r", "SUCCESS", now, 5L, 2, null, null, "{}"));
        log.onPayload(mock(Node.class), new byte[] {2});
        verify(store).finish(eq("r"), eq("SUCCESS"), eq(now), eq(5L), eq(2), any(), any(), eq("{}"));

        decodeReturns(DagRunLogMessage.node("r", "g", "n", 1, "SUCCESS", "in", "out", "exec", 3L, null));
        log.onPayload(mock(Node.class), new byte[] {3});
        verify(store).node(eq("r"), eq("g"), eq("n"), eq(1), eq("SUCCESS"), eq("in"), eq("out"),
                eq("exec"), eq(3L), any());

        decodeReturns(DagRunLogMessage.frontier("r", Set.of("n1")));
        log.onPayload(mock(Node.class), new byte[] {4});
        assertThat(log.frontierOf("r")).containsExactly("n1");

        // empty frontier removes the run's entry
        decodeReturns(DagRunLogMessage.frontier("r", Set.of()));
        log.onPayload(mock(Node.class), new byte[] {5});
        assertThat(log.frontierOf("r")).isEmpty();
    }

    @Test
    void onPayloadLeaderFansOut() {
        when(cluster.isLeader()).thenReturn(true);
        ClusterDagRunLog log = new ClusterDagRunLog(store, cluster, codec, true);
        decodeReturns(DagRunLogMessage.begin("r", null, "app", "g", "t", LocalDateTime.now(), "{}"));
        byte[] content = new byte[] {1};
        log.onPayload(mock(Node.class), content);
        verify(cluster).multicastOn(eq(ClusterDagRunLog.CHANNEL), any(), eq(content), eq(false));
    }

    @Test
    void onPayloadUnknownOpIsIgnored() {
        ClusterDagRunLog log = new ClusterDagRunLog(store, cluster, codec, true);
        decodeReturns(new DagRunLogMessage("weird", "r", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null));
        log.onPayload(mock(Node.class), new byte[] {1});
        verify(store, never()).begin(any(), any(), any(), any(), any(), any(), any());
    }

    private void decodeReturns(DagRunLogMessage message) {
        when(codec.decode(any(), eq(DagRunLogMessage.class))).thenReturn(message);
    }
}
