package com.github.cronsmith.springapp.scheduler.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronsmith.springapp.scheduler.StoreType;

/**
 * A read-only view of the scheduler cluster for the frontend: the member nodes (each a scheduler),
 * which one is the leader, whether group sharding is on, and what store backs them.
 *
 * @Description: ClusterController
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
@RestController
@RequestMapping("/cluster")
public class ClusterController {

    private final GossipCluster cluster;
    private final StoreType storeType;
    private final boolean shardingEnabled;

    public ClusterController(GossipCluster cluster, StoreType storeType, boolean shardingEnabled) {
        this.cluster = cluster;
        this.storeType = storeType;
        this.shardingEnabled = shardingEnabled;
    }

    @GetMapping
    public Map<String, Object> cluster() {
        Node self = cluster.self();
        Node leader = cluster.leader();
        String selfId = self != null ? self.id() : null;
        String leaderId = leader != null ? leader.id() : null;

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Node n : cluster.members()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.id());
            m.put("name", n.name());
            m.put("host", n.host());
            m.put("port", n.port());
            m.put("self", n.id().equals(selfId));
            m.put("leader", n.id().equals(leaderId));
            m.put("role", shardingEnabled ? "sharded" : (n.id().equals(leaderId) ? "leader" : "standby"));
            nodes.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("application", self != null ? self.name() : null);
        out.put("selfId", selfId);
        out.put("leaderId", leaderId);
        out.put("sharding", shardingEnabled);
        out.put("store", storeType.name());
        out.put("storeShared", storeType.isShared());
        out.put("storeReplicated", storeType.isReplicated());
        out.put("storeMetadata", storeType.metadata());
        out.put("nodeCount", nodes.size());
        out.put("nodes", nodes);
        return out;
    }

}
