package com.github.cronsmith.springapp.scheduler;

import com.chaconneai.spreader.event.GossipListener;

/**
 * The scheduler lifecycle the server bootstrap drives, so it can treat either triggering model
 * uniformly. Two implementations:
 * <ul>
 * <li>{@link LeaderSchedulerLifecycle} — only the leader runs the scheduler (the default).</li>
 * <li>{@link ShardedSchedulerLifecycle} — every node runs the scheduler and triggers the task groups
 * that consistent-hash to it (opt-in {@code cronsmith.server.scheduler.sharding} over a shared
 * store).</li>
 * </ul>
 * Both react to cluster membership and leadership changes as a {@link GossipListener}.
 *
 * @Description: SchedulerLifecycle
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public interface SchedulerLifecycle extends GossipListener {

    /** Bring the scheduler(s) in line with the current cluster state. Idempotent. */
    void reconcile();

}
