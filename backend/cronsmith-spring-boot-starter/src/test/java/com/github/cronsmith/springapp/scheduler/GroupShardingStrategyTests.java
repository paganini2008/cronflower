package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;

/**
 * Ownership is a deterministic local computation: every node builds the same ring from the same
 * member list, so each group belongs to exactly one node and they all agree. Degenerate memberships
 * (single node, none, no identity) fall back to "owns everything".
 *
 * @Description: GroupShardingStrategyTests
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
class GroupShardingStrategyTests {

    private static final String APP = "cronsmith-application";

    private static Node node(String id) {
        Node n = mock(Node.class);
        when(n.id()).thenReturn(id);
        when(n.name()).thenReturn(APP);
        when(n.metadata()).thenReturn(Map.of());
        return n;
    }

    /** A strategy for a cluster whose self is {@code self} and whose members are {@code members}. */
    private static GroupShardingStrategy strategyFor(Node self, List<Node> members) {
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.self()).thenReturn(self);
        when(cluster.membersOf(APP)).thenReturn(members);
        return new GroupShardingStrategy(cluster);
    }

    @Test
    void everyGroupIsOwnedByExactlyOneNode() {
        Node a = node("a");
        Node b = node("b");
        Node c = node("c");
        List<Node> members = List.of(a, b, c);
        GroupShardingStrategy sa = strategyFor(a, members);
        GroupShardingStrategy sb = strategyFor(b, members);
        GroupShardingStrategy sc = strategyFor(c, members);

        int ownedByA = 0, ownedByB = 0, ownedByC = 0;
        int groups = 300;
        for (int i = 0; i < groups; i++) {
            String group = "group-" + i;
            int owners = 0;
            if (sa.owns(group)) {
                owners++;
                ownedByA++;
            }
            if (sb.owns(group)) {
                owners++;
                ownedByB++;
            }
            if (sc.owns(group)) {
                owners++;
                ownedByC++;
            }
            assertThat(owners).as("group %s must be owned by exactly one node", group).isEqualTo(1);
        }
        // Every node carries a real share (consistent hashing spreads them; not a strict balance).
        assertThat(ownedByA).isPositive();
        assertThat(ownedByB).isPositive();
        assertThat(ownedByC).isPositive();
        assertThat(ownedByA + ownedByB + ownedByC).isEqualTo(groups);
    }

    @Test
    void ownershipIsStableForTheSameMembership() {
        Node a = node("a");
        Node b = node("b");
        List<Node> members = List.of(a, b);
        GroupShardingStrategy s = strategyFor(a, members);
        for (int i = 0; i < 50; i++) {
            boolean first = s.owns("stable-" + i);
            for (int r = 0; r < 5; r++) {
                assertThat(s.owns("stable-" + i)).isEqualTo(first);
            }
        }
    }

    @Test
    void singleMemberOwnsEverything() {
        Node a = node("a");
        GroupShardingStrategy s = strategyFor(a, List.of(a));
        for (int i = 0; i < 20; i++) {
            assertThat(s.owns("g-" + i)).isTrue();
        }
    }

    @Test
    void noIdentityOrNoMembersOwnsEverything() {
        assertThat(strategyFor(null, List.of()).owns("g")).isTrue();
        Node a = node("a");
        assertThat(strategyFor(a, List.of()).owns("g")).isTrue();
    }
}
