package com.github.cronflow.springapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.cronflow.springapp.ai.DagValidator.Issue;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagDefinition.ChannelDef;
import com.github.cronflow.springapp.pojo.DagDefinition.ConditionalDef;
import com.github.cronflow.springapp.pojo.DagDefinition.EdgeDef;
import com.github.cronflow.springapp.pojo.DagDefinition.NodeDef;
import com.github.cronflow.springapp.pojo.DagDefinition.ShardDef;

class DagValidatorTests {

    private final DagValidator validator = new DagValidator();

    private NodeCatalog catalog(String... names) {
        List<CatalogNode> nodes = new ArrayList<>();
        for (String n : names) {
            nodes.add(new CatalogNode(n, n + " node", n + "Bean", "run", List.of(), List.of()));
        }
        return NodeCatalog.of(nodes);
    }

    private NodeDef node(String name, boolean entry) {
        return new NodeDef(name, entry, "ALL", 0, name + "Bean", "run", null);
    }

    private DagDefinition dag(List<NodeDef> nodes, List<EdgeDef> edges, List<ChannelDef> channels,
            List<ConditionalDef> conds, List<ShardDef> shards) {
        return new DagDefinition("flow", List.of(), channels, nodes, edges, conds, shards);
    }

    @Test
    void validSingleNode() {
        List<Issue> issues = validator.validate(
                dag(List.of(node("start", true)), null, null, null, null), catalog("start"));
        assertThat(issues).isEmpty();
    }

    @Test
    void validTwoNodeChain() {
        var d = dag(List.of(node("a", true), node("b", false)),
                List.of(new EdgeDef("a", "b", null, null)), null, null, null);
        assertThat(validator.validate(d, catalog("a", "b"))).isEmpty();
    }

    @Test
    void nodeNotInCatalog() {
        var d = dag(List.of(node("ghost", true)), null, null, null, null);
        assertThat(validator.validate(d, catalog("real")))
                .anyMatch(i -> i.message().contains("not in the catalog"));
    }

    @Test
    void noEntryNode() {
        var d = dag(List.of(node("a", false)), null, null, null, null);
        assertThat(validator.validate(d, catalog("a")))
                .anyMatch(i -> i.message().contains("no entry node"));
    }

    @Test
    void duplicateNodeName() {
        var d = dag(List.of(node("a", true), node("a", false)), null, null, null, null);
        assertThat(validator.validate(d, catalog("a")))
                .anyMatch(i -> i.message().contains("duplicate node name"));
    }

    @Test
    void edgeToUnknownNode() {
        var d = dag(List.of(node("a", true)), List.of(new EdgeDef("a", "nope", null, null)), null, null,
                null);
        assertThat(validator.validate(d, catalog("a")))
                .anyMatch(i -> i.path().contains(".to") && i.message().contains("nope"));
    }

    @Test
    void cycleIsRejected() {
        var d = dag(List.of(node("a", true), node("b", false)),
                List.of(new EdgeDef("a", "b", null, null), new EdgeDef("b", "a", null, null)), null,
                null, null);
        assertThat(validator.validate(d, catalog("a", "b")))
                .anyMatch(i -> i.message().contains("cycle"));
    }

    @Test
    void unknownReducer() {
        var d = dag(List.of(node("a", true)), null, List.of(new ChannelDef("c", "sum")), null, null);
        assertThat(validator.validate(d, catalog("a")))
                .anyMatch(i -> i.message().contains("unknown reducer"));
    }

    @Test
    void knownReducerAndBlankReducerAreFine() {
        var d = dag(List.of(node("a", true)),
                null, List.of(new ChannelDef("c", "joinCsv"), new ChannelDef("d", null)), null, null);
        assertThat(validator.validate(d, catalog("a"))).isEmpty();
    }

    @Test
    void badTrigger() {
        var n = new NodeDef("a", true, "SOMETIMES", 0, "aBean", "run", null);
        var d = dag(List.of(n), null, null, null, null);
        assertThat(validator.validate(d, catalog("a")))
                .anyMatch(i -> i.message().contains("trigger must be ALL or ANY"));
    }

    @Test
    void shardUnknownNodeAndBadSize() {
        var d = dag(List.of(node("a", true)), null, null, null,
                List.of(new ShardDef("ghost", "in", "out", 0)));
        List<Issue> issues = validator.validate(d, catalog("a"));
        assertThat(issues).anyMatch(i -> i.message().contains("shard targets unknown node"));
        assertThat(issues).anyMatch(i -> i.message().contains("size must be greater than zero"));
    }

    @Test
    void conditionalBranchUnknownTarget() {
        var cond = new ConditionalDef(List.of("c"), "expression", "#c > 1", List.of(),
                Map.of("hi", List.of("ghost")), List.of());
        var d = dag(List.of(node("a", true)), null, null, List.of(cond), null);
        assertThat(validator.validate(d, catalog("a")))
                .anyMatch(i -> i.message().contains("branch targets unknown node 'ghost'"));
    }

    @Test
    void nullDagAndEmptyNodes() {
        assertThat(validator.validate(null, catalog("a"))).anyMatch(i -> i.message().contains("null"));
        var d = new DagDefinition("flow", List.of(), null, List.of(), null, null, null);
        assertThat(validator.validate(d, catalog("a")))
                .anyMatch(i -> i.message().contains("at least one node"));
    }
}
