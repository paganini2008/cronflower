package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.openspreader.serialization.ObjectCodecs;
import com.chaconneai.openspreader.serialization.SerializationType;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.NodeState;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskException;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;

/**
 * Unit tests for the cluster routing wrapper: reads stay on the local delegate, a leader applies
 * writes locally, a replicated leader also broadcasts (excluding itself), and a follower depends on
 * the leader being reachable.
 *
 * @Description: ClusterTaskManagerTests
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
class ClusterTaskManagerTests {

    private static final ObjectCodec CODEC = ObjectCodecs.create(SerializationType.JDK);

    private static Node node(String id, String host) {
        return new Node(id, "default", host, 22000, 0L, 0L, NodeState.ALIVE, 0, null);
    }

    private static ClusterTaskManager manager(TaskManager delegate, GossipCluster cluster,
            boolean replicated) {
        StoreType storeType = replicated ? StoreType.SQLITE : StoreType.MYSQL;
        return new ClusterTaskManager(delegate, cluster, CODEC, storeType, replicated, 150L);
    }

    @Test
    void readsAreServedFromTheLocalDelegate() {
        TaskManager delegate = mock(TaskManager.class);
        GossipCluster cluster = mock(GossipCluster.class);
        TaskDetail detail = mock(TaskDetail.class);
        when(delegate.getTaskDetail(TaskId.of("j"), false)).thenReturn(detail);

        ClusterTaskManager manager = manager(delegate, cluster, false);
        assertThat(manager.getTaskDetail(TaskId.of("j"), false)).isSameAs(detail);

        // A read never touches the cluster.
        verify(cluster, never()).multicastOn(any(), any(), any(), Mockito.anyBoolean());
        verify(cluster, never()).sendToLeaderOn(any(), any());
    }

    @Test
    void leaderAppliesWriteLocallyAndDoesNotBroadcastWhenNotReplicated() {
        TaskManager delegate = mock(TaskManager.class);
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.isLeader()).thenReturn(true);
        TaskDetail saved = mock(TaskDetail.class);
        HttpDispatchCustomTask task = task();
        when(delegate.saveTask(task, "p")).thenReturn(saved);

        ClusterTaskManager manager = manager(delegate, cluster, false);
        assertThat(manager.saveTask(task, "p")).isSameAs(saved);

        verify(delegate).saveTask(task, "p");
        // Shared storage: nothing is broadcast.
        verify(cluster, never()).multicastOn(any(), any(), any(), Mockito.anyBoolean());
    }

    @Test
    void replicatedLeaderBroadcastsTheCommittedWriteExcludingSelf() {
        TaskManager delegate = mock(TaskManager.class);
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.isLeader()).thenReturn(true);
        HttpDispatchCustomTask task = task();

        ClusterTaskManager manager = manager(delegate, cluster, true);
        manager.saveTask(task, "p");

        verify(delegate).saveTask(task, "p");
        // Node-local storage: the leader applied locally, then multicasts to everyone else.
        verify(cluster).multicastOn(eq(ClusterTaskManager.CHANNEL), isNull(), any(byte[].class),
                eq(false));
    }

    @Test
    void followerFailsWhenNoLeaderIsReachable() {
        TaskManager delegate = mock(TaskManager.class);
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.isLeader()).thenReturn(false);
        when(cluster.self()).thenReturn(node("self", "hostB"));
        when(cluster.sendToLeaderOn(any(), any())).thenReturn(false);

        ClusterTaskManager manager = manager(delegate, cluster, false);
        assertThatThrownBy(() -> manager.saveTask(task(), "p"))
                .isInstanceOf(TaskException.class)
                .hasMessageContaining("No leader");
        verify(delegate, never()).saveTask(any(), any());
    }

    @Test
    void followerTimesOutWhenLeaderNeverAnswers() {
        TaskManager delegate = mock(TaskManager.class);
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.isLeader()).thenReturn(false);
        when(cluster.self()).thenReturn(node("self", "hostB"));
        when(cluster.sendToLeaderOn(any(), any())).thenReturn(true); // sent, but no reply comes back

        ClusterTaskManager manager = manager(delegate, cluster, false);
        assertThatThrownBy(() -> manager.saveTask(task(), "p"))
                .isInstanceOf(TaskException.class);
    }

    @Test
    void broadcastApplyFromADifferentHostIsAppliedToTheLocalCopy() {
        TaskManager delegate = mock(TaskManager.class);
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.self()).thenReturn(node("self", "hostA"));
        ClusterTaskManager manager = manager(delegate, cluster, true);

        HttpDispatchCustomTask task = task();
        byte[] payload =
                CODEC.encode(ClusterMessage.apply(ClusterOp.SAVE_TASK, new Object[] {task, "p"}));
        // A committed write broadcast by the leader on another host: this node holds a separate copy,
        // so it must apply it.
        manager.onPayload(node("leader", "hostB"), payload);

        // The task crosses the wire (serialized), so match on any Task with the same parameter.
        verify(delegate).saveTask(any(), eq("p"));
    }

    @Test
    void broadcastApplyFromTheSameHostIsAlsoAppliedToItsOwnCopy() {
        TaskManager delegate = mock(TaskManager.class);
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.self()).thenReturn(node("self", "hostA"));
        ClusterTaskManager manager = manager(delegate, cluster, true);

        HttpDispatchCustomTask task = task();
        byte[] payload =
                CODEC.encode(ClusterMessage.apply(ClusterOp.SAVE_TASK, new Object[] {task, "p"}));
        // Node-local storage is independent per node: even a co-located node holds its OWN copy, so it
        // must apply the leader's broadcast (otherwise it would be empty and a failover would lose data).
        manager.onPayload(node("leader", "hostA"), payload);

        verify(delegate).saveTask(any(), eq("p"));
    }

    private static HttpDispatchCustomTask task() {
        return HttpDispatchCustomTask.fromMetadata(new ExecutorTaskMetadata("grp", "job",
                "com.demo.Tasks", "demoTasks", "run", "0 0 12 * * ?", "d", "hello", 0L, 0, 1000L,
                "FIRE_ONCE_NOW"), "demo-app");
    }
}
