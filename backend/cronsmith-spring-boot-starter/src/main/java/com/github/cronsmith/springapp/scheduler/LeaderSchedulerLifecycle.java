package com.github.cronsmith.springapp.scheduler;

import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chaconneai.spreader.Node;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;

/**
 * Runs the cronsmith scheduler on the leader, and only there. A fresh {@link TimeWheelScheduler} is
 * built and started when this node becomes leader (it restores the schedule from the store), and
 * closed when it stops being leader.
 *
 * <p>
 * {@link #reconcile()} is idempotent — it acts on {@code isLeader()} and the current running state —
 * so a duplicate leadership event is a harmless no-op, which is why no leader-id bookkeeping is
 * needed here.
 *
 * @Description: LeaderSchedulerLifecycle
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class LeaderSchedulerLifecycle implements SchedulerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LeaderSchedulerLifecycle.class);

    private final TaskManager taskManager;
    private final com.chaconneai.spreader.GossipCluster cluster;
    private final ZoneId zoneId;
    private final long claimWindowMillis;
    private final long claimIntervalMillis;
    private final Object lock = new Object();

    private TimeWheelScheduler scheduler;

    public LeaderSchedulerLifecycle(TaskManager taskManager,
            com.chaconneai.spreader.GossipCluster cluster, ZoneId zoneId, long claimWindowMillis,
            long claimIntervalMillis) {
        this.taskManager = taskManager;
        this.cluster = cluster;
        this.zoneId = zoneId;
        this.claimWindowMillis = claimWindowMillis;
        this.claimIntervalMillis = claimIntervalMillis;
    }

    public void reconcile() {
        synchronized (lock) {
            boolean leader = cluster.isLeader();
            if (leader && scheduler == null) {
                start();
            } else if (!leader && scheduler != null) {
                stop();
            }
        }
    }

    private void start() {
        TimeWheelScheduler wheel = new TimeWheelScheduler();
        wheel.setTaskManager(taskManager);
        wheel.setZoneId(zoneId);
        if (claimWindowMillis > 0) {
            wheel.setClaimWindow(claimWindowMillis);
            wheel.setClaimInterval(claimIntervalMillis);
        }
        wheel.start();
        scheduler = wheel;
        log.info("This node is the leader; cronsmith scheduler started");
    }

    private void stop() {
        try {
            scheduler.close();
        } catch (Exception e) {
            log.warn("Error while stopping the scheduler", e);
        } finally {
            scheduler = null;
            log.info("This node is no longer the leader; cronsmith scheduler stopped");
        }
    }

    /** The running scheduler, or null when this node is not the leader. */
    public TimeWheelScheduler currentScheduler() {
        synchronized (lock) {
            return scheduler;
        }
    }

    /** Schedule a task now, if this node is the leader. A no-op elsewhere. */
    public void scheduleIfLeader(TaskId taskId) {
        synchronized (lock) {
            if (scheduler != null) {
                try {
                    scheduler.schedule(taskId);
                } catch (Exception e) {
                    log.warn("Could not schedule task {}", taskId, e);
                }
            }
        }
    }

    /**
     * On the leader, pick up any task sitting in standby — one registered after start, or by another
     * node — and schedule it. Cheap and idempotent: already-scheduled tasks are not in standby.
     */
    public void reconcilePending() {
        synchronized (lock) {
            if (scheduler == null) {
                return;
            }
            try {
                for (TaskDetail detail : taskManager
                        .findTaskDetails(TaskQuery.newQuery().statuses(TaskStatus.STANDBY))) {
                    try {
                        scheduler.schedule(detail.getTaskId());
                    } catch (Exception e) {
                        log.warn("Could not schedule pending task {}", detail.getTaskId(), e);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not reconcile pending tasks", e);
            }
        }
    }

    @Override
    public void onClusterJoined(Node self, boolean alone) {
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
