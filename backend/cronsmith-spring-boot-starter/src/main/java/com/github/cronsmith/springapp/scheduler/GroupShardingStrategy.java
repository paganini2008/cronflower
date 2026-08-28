package com.github.cronsmith.springapp.scheduler;

import java.util.List;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.loadbalance.ConsistentHashLoadBalancer;
import com.github.cronsmith.springapp.scheduler.TaskId;

/**
 * Decides which cluster node owns a task group, by consistent-hashing the group name over the current
 * membership.
 *
 * <p>
 * There is no coordinator. Every node builds the <b>same</b> ring from the same gossip-synced member
 * list (the balancer sorts members before hashing), so a group belongs to exactly one node and all
 * nodes agree on which — independently. When a node joins or leaves, only about {@code 1/N} of the
 * groups move.
 *
 * <p>
 * Ownership is advisory: it spreads load and decides who normally triggers a group. Correctness in the
 * seconds when members' views differ rests on the shared store's atomic
 * {@link com.github.cronsmith.springapp.scheduler.TaskManager#compareAndSetTaskStatus}, which lets at most one
 * node win a given fire. That is why sharding is only enabled against a shared store.
 *
 * @Description: GroupShardingStrategy
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public class GroupShardingStrategy {

    private final GossipCluster cluster;

    // One instance, reused on purpose: the ring caches itself and only rebuilds when membership
    // actually changes. A fresh balancer per call would rebuild the whole ring every time (see the
    // ConsistentHashLoadBalancer javadoc).
    private final ConsistentHashLoadBalancer balancer = new ConsistentHashLoadBalancer();

    public GroupShardingStrategy(GossipCluster cluster) {
        this.cluster = cluster;
    }

    /** Whether this node owns the given task's group. */
    public boolean owns(TaskId taskId) {
        return owns(taskId.getGroup());
    }

    /** Whether this node owns the given group. */
    public boolean owns(String group) {
        Node self = cluster.self();
        if (self == null) {
            // No cluster identity yet (very early start-up): behave as if alone.
            return true;
        }
        // Only nodes of this same server application take part in the ring: executors are not cluster
        // members, and another application must not be handed our groups.
        List<Node> members = cluster.membersOf(self.name());
        if (members == null || members.isEmpty()) {
            return true;
        }
        Node owner = balancer.choose(members, group);
        return owner == null || owner.id().equals(self.id());
    }

}
