package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.openspreader.serialization.ObjectCodecs;
import com.chaconneai.openspreader.serialization.SerializationType;
import com.chaconneai.spreader.GossipCluster;

/**
 * Rounds out {@link ClusterTaskManager} coverage: every read delegates to the local store and never
 * touches the cluster, and every write, on a leader, is applied to the local store.
 */
class ClusterTaskManagerDelegationTests {

    private static final ObjectCodec CODEC = ObjectCodecs.create(SerializationType.JDK);

    private final TaskManager delegate = mock(TaskManager.class);
    private final GossipCluster cluster = mock(GossipCluster.class);
    private final ClusterTaskManager manager =
            new ClusterTaskManager(delegate, cluster, CODEC, StoreType.MYSQL, false, 150L);

    @Test
    void everyReadIsServedByTheDelegateWithoutTouchingTheCluster() {
        TaskId id = TaskId.of("g", "n");
        LocalDateTime now = LocalDateTime.now();
        TaskQuery query = TaskQuery.newQuery();
        when(delegate.hasTask(id)).thenReturn(true);
        when(delegate.getTaskCount(query)).thenReturn(3);
        when(delegate.findTaskDetails(query)).thenReturn(List.of());
        when(delegate.findNextFiredDateTimes(id, now, now)).thenReturn(List.of(now));
        when(delegate.findUpcomingTasksBetween(now, now)).thenReturn(List.of(id));
        when(delegate.findExecutionLogs(id, 10, 0)).thenReturn(List.of());

        assertThat(manager.getStoreType()).isEqualTo(StoreType.MYSQL);
        assertThat(manager.hasTask(id)).isTrue();
        assertThat(manager.getTaskCount(query)).isEqualTo(3);
        assertThat(manager.findTaskDetails(query)).isEmpty();
        assertThat(manager.findNextFiredDateTimes(id, now, now)).containsExactly(now);
        assertThat(manager.findUpcomingTasksBetween(now, now)).containsExactly(id);
        assertThat(manager.findExecutionLogs(id, 10, 0)).isEmpty();

        TaskRestoreHandler handler = mock(TaskRestoreHandler.class);
        manager.restoreTasks(handler);
        verify(delegate).restoreTasks(handler);
        manager.close();
        verify(delegate).close();

        verify(cluster, never()).multicastOn(any(), any(), any(), Mockito.anyBoolean());
        verify(cluster, never()).sendToLeaderOn(any(), any());
    }

    @Test
    void aLeaderAppliesEveryWriteToTheLocalStore() {
        when(cluster.isLeader()).thenReturn(true);
        TaskId id = TaskId.of("g", "n");
        LocalDateTime when = LocalDateTime.now();

        manager.removeTask(id);
        verify(delegate).removeTask(id);

        manager.computeNextFiredDateTime(id, when);
        verify(delegate).computeNextFiredDateTime(id, when);

        manager.setTaskStatus(id, TaskStatus.PAUSED);
        verify(delegate).setTaskStatus(id, TaskStatus.PAUSED);

        manager.compareAndSetTaskStatus(id, TaskStatus.STANDBY, TaskStatus.RUNNING);
        verify(delegate).compareAndSetTaskStatus(id, TaskStatus.STANDBY, TaskStatus.RUNNING);

        TaskExecutionLog log = new TaskExecutionLog(id, when);
        manager.recordExecution(log);
        verify(delegate).recordExecution(log);

        manager.recordMisfire(id, when);
        verify(delegate).recordMisfire(id, when);

        // Shared store: applied locally, never broadcast.
        verify(cluster, never()).multicastOn(any(), any(), any(), Mockito.anyBoolean());
    }
}
