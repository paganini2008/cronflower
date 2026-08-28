package com.github.cronsmith.springapp.scheduler.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronsmith.springapp.scheduler.StoreType;

/**
 * Unit tests for the read-only cluster view: node listing, leader/self flags, sharding role, and the
 * empty-cluster edge.
 */
class ClusterControllerTests {

    private static Node node(String id, String name, int port) {
        Node n = mock(Node.class);
        when(n.id()).thenReturn(id);
        when(n.name()).thenReturn(name);
        when(n.host()).thenReturn("10.0.0.1");
        when(n.port()).thenReturn(port);
        return n;
    }

    @Test
    void reportsNodesLeaderAndStore() {
        Node self = node("n1", "cronsmith", 22000);
        Node other = node("n2", "cronsmith", 22001);
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.self()).thenReturn(self);
        when(cluster.leader()).thenReturn(self);
        when(cluster.members()).thenReturn(List.of(self, other));

        Map<String, Object> out = new ClusterController(cluster, StoreType.IN_MEMORY, false).cluster();
        assertThat(out.get("nodeCount")).isEqualTo(2);
        assertThat(out.get("selfId")).isEqualTo("n1");
        assertThat(out.get("leaderId")).isEqualTo("n1");
        assertThat(out.get("store")).isEqualTo(StoreType.IN_MEMORY.name());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) out.get("nodes");
        assertThat(nodes.get(0).get("leader")).isEqualTo(true);
        assertThat(nodes.get(0).get("role")).isEqualTo("leader");
        assertThat(nodes.get(1).get("role")).isEqualTo("standby");
    }

    @Test
    void marksEveryNodeShardedWhenShardingIsOn() {
        Node self = node("n1", "cronsmith", 22000);
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.self()).thenReturn(self);
        when(cluster.leader()).thenReturn(self);
        when(cluster.members()).thenReturn(List.of(self));

        Map<String, Object> out = new ClusterController(cluster, StoreType.IN_MEMORY, true).cluster();
        assertThat(out.get("sharding")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) out.get("nodes");
        assertThat(nodes.get(0).get("role")).isEqualTo("sharded");
    }

    @Test
    void toleratesAClusterWithNoSelfOrLeader() {
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.self()).thenReturn(null);
        when(cluster.leader()).thenReturn(null);
        when(cluster.members()).thenReturn(List.of());

        Map<String, Object> out = new ClusterController(cluster, StoreType.IN_MEMORY, false).cluster();
        assertThat(out.get("nodeCount")).isEqualTo(0);
        assertThat(out.get("selfId")).isNull();
        assertThat(out.get("application")).isNull();
    }
}
