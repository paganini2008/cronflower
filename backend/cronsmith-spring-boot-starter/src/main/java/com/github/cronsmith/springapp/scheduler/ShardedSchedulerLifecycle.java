package com.github.cronsmith.springapp.scheduler;

import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;

/**
 * Runs the cronsmith scheduler on <b>every</b> node, each triggering only the task groups that
 * consistent-hash to it. The scheduler is fed a {@link ShardingTaskManager}, so its windowed claim
 * only ever parks this node's share.
 *
 * <p>
 * Re-sharding is event-driven: any membership or leadership change re-evaluates ownership at once via
 * {@link TimeWheelScheduler#claimNow()} instead of waiting for the next claim interval. Newly-owned
 * groups are picked up by that claim; groups this node no longer owns are released lazily — when such
 * a task next comes due, {@link ShardingTaskManager}'s fire guard refuses it here and the new owner
 * runs it. A node that dies leaves its parked tasks behind as {@code SCHEDULED} rows in the shared
 * store, which survivors then claim as they come due.
 *
 * <p>
 * There is no coordinator and no leader gating: ownership is a deterministic local computation over
 * the gossip-synced member list, and the shared store's atomic compare-and-set keeps a task from
 * firing twice during the seconds when members' views differ. Sharding is therefore only wired up
 * against a shared store.
 *
 * @Description: ShardedSchedulerLifecycle
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public class ShardedSchedulerLifecycle implements SchedulerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ShardedSchedulerLifecycle.class);

    /** Sharding needs windowed claiming; if none is configured, fall back to five minutes. */
    private static final long DEFAULT_WINDOW_MILLIS = 5 * 60_000L;

    private final TaskManager shardingTaskManager;
    private final GossipCluster cluster;
    private final ZoneId zoneId;
    private final long claimWindowMillis;
    private final long claimIntervalMillis;
    private final Object lock = new Object();

    private TimeWheelScheduler scheduler;

    public ShardedSchedulerLifecycle(TaskManager shardingTaskManager, GossipCluster cluster,
            ZoneId zoneId, long claimWindowMillis, long claimIntervalMillis) {
        this.shardingTaskManager = shardingTaskManager;
        this.cluster = cluster;
        this.zoneId = zoneId;
        this.claimWindowMillis = claimWindowMillis;
        this.claimIntervalMillis = claimIntervalMillis;
    }

    @Override
    public void reconcile() {
        synchronized (lock) {
            if (scheduler == null) {
                start();
            } else {
                // Membership or leadership may have changed: re-evaluate ownership now rather than at
                // the next claim tick. claimNow() picks up groups newly ours; the fire guard releases
                // the ones no longer ours.
                scheduler.claimNow();
            }
        }
    }

    private void start() {
        long window = claimWindowMillis > 0 ? claimWindowMillis : DEFAULT_WINDOW_MILLIS;
        if (claimWindowMillis <= 0) {
            log.warn("Sharding requires windowed claiming; no window configured, defaulting to {}ms",
                    window);
        }
        TimeWheelScheduler wheel = new TimeWheelScheduler();
        wheel.setTaskManager(shardingTaskManager);
        wheel.setZoneId(zoneId);
        wheel.setClaimWindow(window);
        wheel.setClaimInterval(claimIntervalMillis);
        wheel.start();
        scheduler = wheel;
        log.info("cronsmith sharded scheduler started on this node; it triggers its group-hash share");
    }

    private void stop() {
        try {
            scheduler.close();
        } catch (Exception e) {
            log.warn("Error while stopping the sharded scheduler", e);
        } finally {
            scheduler = null;
            log.info("cronsmith sharded scheduler stopped on this node");
        }
    }

    /** The running scheduler, or null before it has started. */
    public TimeWheelScheduler currentScheduler() {
        synchronized (lock) {
            return scheduler;
        }
    }

    @Override
    public void onClusterJoined(Node self, boolean alone) {
        reconcile();
    }

    @Override
    public void onNodeJoined(Node node) {
        reconcile();
    }

    @Override
    public void onNodeLeft(Node node, boolean graceful) {
        reconcile();
    }

    @Override
    public void onLeaderChanged(Node previous, Node current, boolean selfIsLeader) {
        reconcile();
    }

    @Override
    public void onLeaderLeft(Node node) {
        reconcile();
    }

    @Override
    public void onLeaderBack(Node node) {
        reconcile();
    }

    @Override
    public void onSelfStopped(Node self) {
        synchronized (lock) {
            if (scheduler != null) {
                stop();
            }
        }
    }

}
