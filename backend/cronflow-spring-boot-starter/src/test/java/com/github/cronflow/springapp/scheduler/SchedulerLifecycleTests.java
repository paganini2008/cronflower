package com.github.cronflow.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;

/**
 * Covers the leader-only and sharded scheduler lifecycles: starting/stopping a real timing wheel on
 * leadership and membership transitions (against an in-memory task manager, never a networked store).
 */
class SchedulerLifecycleTests {

    private final ZoneId utc = ZoneId.of("UTC");

    @Test
    void leaderLifecycleStartsOnLeadershipAndStopsWhenNotLeader() {
        GossipCluster cluster = mock(GossipCluster.class);
        TaskManager tm = new InMemoryTaskManager();
        LeaderSchedulerLifecycle lifecycle =
                new LeaderSchedulerLifecycle(tm, cluster, utc, 1000L, 500L);

        when(cluster.isLeader()).thenReturn(false);
        lifecycle.reconcile();
        assertThat(lifecycle.currentScheduler()).isNull();

        when(cluster.isLeader()).thenReturn(true);
        lifecycle.onClusterJoined(mock(Node.class), true); // -> reconcile -> start
        assertThat(lifecycle.currentScheduler()).isNotNull();

        // idempotent events while leader
        lifecycle.onLeaderChanged(null, mock(Node.class), true);
        lifecycle.onLeaderBack(mock(Node.class));
        lifecycle.scheduleIfLeader(TaskId.of("grp", "nope")); // unknown task -> swallowed
        lifecycle.reconcilePending(); // nothing in standby

        when(cluster.isLeader()).thenReturn(false);
        lifecycle.onLeaderLeft(mock(Node.class)); // -> reconcile -> stop
        assertThat(lifecycle.currentScheduler()).isNull();

        // scheduleIfLeader / reconcilePending are no-ops with no scheduler
        lifecycle.scheduleIfLeader(TaskId.of("grp", "nope"));
        lifecycle.reconcilePending();
        lifecycle.onSelfStopped(mock(Node.class)); // safe when already stopped
    }

    @Test
    void leaderLifecycleWithoutClaimWindow() {
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.isLeader()).thenReturn(true);
        LeaderSchedulerLifecycle lifecycle =
                new LeaderSchedulerLifecycle(new InMemoryTaskManager(), cluster, utc, 0L, 0L);
        lifecycle.reconcile();
        assertThat(lifecycle.currentScheduler()).isNotNull();
        lifecycle.onSelfStopped(mock(Node.class));
        assertThat(lifecycle.currentScheduler()).isNull();
    }

    @Test
    void shardedLifecycleStartsOnFirstReconcileAndReclaimsOnChanges() {
        GossipCluster cluster = mock(GossipCluster.class);
        ShardedSchedulerLifecycle lifecycle = new ShardedSchedulerLifecycle(new InMemoryTaskManager(),
                cluster, utc, 1000L, 500L);
        lifecycle.onClusterJoined(mock(Node.class), false); // -> reconcile -> start
        assertThat(lifecycle.currentScheduler()).isNotNull();

        // subsequent events re-claim (scheduler already running)
        lifecycle.onNodeJoined(mock(Node.class));
        lifecycle.onNodeLeft(mock(Node.class), true);
        lifecycle.onLeaderChanged(null, mock(Node.class), false);
        lifecycle.onLeaderLeft(mock(Node.class));
        lifecycle.onLeaderBack(mock(Node.class));
        lifecycle.reconcile();

        lifecycle.onSelfStopped(mock(Node.class)); // -> stop
        assertThat(lifecycle.currentScheduler()).isNull();
    }

    @Test
    void shardedLifecycleDefaultsClaimWindowWhenUnset() {
        GossipCluster cluster = mock(GossipCluster.class);
        ShardedSchedulerLifecycle lifecycle = new ShardedSchedulerLifecycle(new InMemoryTaskManager(),
                cluster, utc, 0L, 500L); // no window -> warn + default
        lifecycle.reconcile();
        assertThat(lifecycle.currentScheduler()).isNotNull();
        lifecycle.onSelfStopped(mock(Node.class));
    }
}
