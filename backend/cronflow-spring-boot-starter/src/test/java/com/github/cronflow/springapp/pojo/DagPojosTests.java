package com.github.cronflow.springapp.pojo;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Constructs every cronflow DAG DTO (server-side copies) and exercises its accessors and factory
 * methods, so the serialisable data model is covered without a running server.
 */
class DagPojosTests {

    private DagDefinition sampleDefinition() {
        return new DagDefinition("g",
                List.of("input"),
                List.of(new DagDefinition.ChannelDef("steps", "concatList")),
                List.of(new DagDefinition.NodeDef("n1", true, "ALL", 2, "bean", "method", "")),
                List.of(new DagDefinition.EdgeDef("n1", "n2", "ON_SUCCESS", null)),
                List.of(new DagDefinition.ConditionalDef(List.of("n1"), "predicate", null,
                        List.of("#x > 1"), Map.of("0", List.of("n2")), List.of("n3"))),
                List.of(new DagDefinition.ShardDef("n1", "in", "out", 4)));
    }

    @Test
    void dagDefinitionAndNestedRecords() {
        DagDefinition d = sampleDefinition();
        assertThat(d.graph()).isEqualTo("g");
        assertThat(d.inputs()).containsExactly("input");
        assertThat(d.channels().get(0).name()).isEqualTo("steps");
        assertThat(d.channels().get(0).reducer()).isEqualTo("concatList");
        DagDefinition.NodeDef n = d.nodes().get(0);
        assertThat(n.name()).isEqualTo("n1");
        assertThat(n.entry()).isTrue();
        assertThat(n.trigger()).isEqualTo("ALL");
        assertThat(n.retries()).isEqualTo(2);
        assertThat(n.beanName()).isEqualTo("bean");
        assertThat(n.methodName()).isEqualTo("method");
        assertThat(n.subgraph()).isEmpty();
        DagDefinition.EdgeDef e = d.edges().get(0);
        assertThat(e.from()).isEqualTo("n1");
        assertThat(e.to()).isEqualTo("n2");
        assertThat(e.condition()).isEqualTo("ON_SUCCESS");
        assertThat(e.branch()).isNull();
        DagDefinition.ConditionalDef c = d.conditionals().get(0);
        assertThat(c.sources()).containsExactly("n1");
        assertThat(c.form()).isEqualTo("predicate");
        assertThat(c.predicates()).containsExactly("#x > 1");
        assertThat(c.branches()).containsKey("0");
        assertThat(c.elseTargets()).containsExactly("n3");
        DagDefinition.ShardDef s = d.shards().get(0);
        assertThat(s.node()).isEqualTo("n1");
        assertThat(s.input()).isEqualTo("in");
        assertThat(s.output()).isEqualTo("out");
        assertThat(s.size()).isEqualTo(4);
        assertThat(d).isEqualTo(sampleDefinition());
        assertThat(d.toString()).contains("g");
    }

    @Test
    void graphAndGraphViewsAndPages() {
        DagDefinition d = sampleDefinition();
        DagGraph g = new DagGraph("app", d);
        assertThat(g.application()).isEqualTo("app");
        assertThat(g.definition()).isSameAs(d);
        DagGraphView v = DagGraphView.of(g);
        assertThat(v.application()).isEqualTo("app");
        assertThat(v.graph()).isEqualTo("g");
        assertThat(v.nodeCount()).isEqualTo(1);
        assertThat(v.definition()).isSameAs(d);
        // null-nodes definition -> nodeCount 0
        DagDefinition empty = new DagDefinition("e", null, null, null, null, null, null);
        assertThat(DagGraphView.of(new DagGraph("a", empty)).nodeCount()).isZero();
        DagGraphPage gp = new DagGraphPage(5, List.of(v));
        assertThat(gp.total()).isEqualTo(5);
        assertThat(gp.items()).hasSize(1);
    }

    @Test
    void runViewsPagesAndDetail() {
        LocalDateTime now = LocalDateTime.now();
        DagRunView run = new DagRunView("r1", "p1", "app", "g", "task", "RUNNING", now, null, 10L, 2,
                "bad", "{}", "{}", null);
        assertThat(run.runId()).isEqualTo("r1");
        assertThat(run.parentRunId()).isEqualTo("p1");
        assertThat(run.application()).isEqualTo("app");
        assertThat(run.graph()).isEqualTo("g");
        assertThat(run.triggeredBy()).isEqualTo("task");
        assertThat(run.status()).isEqualTo("RUNNING");
        assertThat(run.startedAt()).isEqualTo(now);
        assertThat(run.elapsedMs()).isEqualTo(10L);
        assertThat(run.nodeCount()).isEqualTo(2);
        assertThat(run.failedNode()).isEqualTo("bad");
        DagNodeView node = new DagNodeView("r1", "g", "n1", 0, "SUCCESS", "{}", "{}", "exec", 5L, null,
                now);
        assertThat(node.runId()).isEqualTo("r1");
        assertThat(node.node()).isEqualTo("n1");
        assertThat(node.seq()).isZero();
        assertThat(node.executor()).isEqualTo("exec");
        assertThat(node.elapsedMs()).isEqualTo(5L);
        assertThat(node.loggedAt()).isEqualTo(now);
        DagRunPage rp = new DagRunPage(3, List.of(run));
        assertThat(rp.total()).isEqualTo(3);
        assertThat(rp.items()).containsExactly(run);
        DagRunDetail detail = new DagRunDetail(run, List.of(node), List.of(), List.of("n1"));
        assertThat(detail.run()).isSameAs(run);
        assertThat(detail.nodes()).hasSize(1);
        assertThat(detail.children()).isEmpty();
        assertThat(detail.running()).containsExactly("n1");
    }

    @Test
    void registrationHeartbeatAndSyncMessages() {
        DagDefinition d = sampleDefinition();
        TriggerBinding tb = new TriggerBinding("grp", "name", "g");
        assertThat(tb.taskGroup()).isEqualTo("grp");
        assertThat(tb.taskName()).isEqualTo("name");
        assertThat(tb.graph()).isEqualTo("g");
        DagRegistrationRequest reg = new DagRegistrationRequest("app", "i1", "run", "health",
                List.of(d), List.of(tb), 3);
        assertThat(reg.application()).isEqualTo("app");
        assertThat(reg.instanceId()).isEqualTo("i1");
        assertThat(reg.runUrl()).isEqualTo("run");
        assertThat(reg.healthCheckUrl()).isEqualTo("health");
        assertThat(reg.dags()).hasSize(1);
        assertThat(reg.triggers()).hasSize(1);
        assertThat(reg.weight()).isEqualTo(3);
        assertThat(new DagRegistrationResponse("i1").instanceId()).isEqualTo("i1");
        DagHeartbeatRequest hb = new DagHeartbeatRequest("app", "i1", "run", "health", 1);
        assertThat(hb.application()).isEqualTo("app");
        assertThat(hb.instanceId()).isEqualTo("i1");
        assertThat(hb.weight()).isEqualTo(1);
        assertThat(new DagHeartbeatResponse(true).known()).isTrue();
        DagSyncMessage r = DagSyncMessage.register(reg);
        assertThat(r.registration()).isSameAs(reg);
        assertThat(r.heartbeat()).isNull();
        DagSyncMessage h = DagSyncMessage.heartbeat(hb);
        assertThat(h.heartbeat()).isSameAs(hb);
        assertThat(h.registration()).isNull();
    }

    @Test
    void nodeRunRequestResultShardAndStored() {
        NodeRunRequest req = new NodeRunRequest("g", "n", "bean", "m", Map.of("k", "v"));
        assertThat(req.graph()).isEqualTo("g");
        assertThat(req.node()).isEqualTo("n");
        assertThat(req.beanName()).isEqualTo("bean");
        assertThat(req.methodName()).isEqualTo("m");
        assertThat(req.state()).containsEntry("k", "v");
        NodeRunResult res = new NodeRunResult(true, Map.of("o", 1), null);
        assertThat(res.ok()).isTrue();
        assertThat(res.updates()).containsEntry("o", 1);
        assertThat(res.errorDetail()).isNull();
        ShardPayload sp = new ShardPayload("g", "bean", "m", "out", List.of(1, 2), 10);
        assertThat(sp.graph()).isEqualTo("g");
        assertThat(sp.bean()).isEqualTo("bean");
        assertThat(sp.method()).isEqualTo("m");
        assertThat(sp.output()).isEqualTo("out");
        assertThat(sp.items()).containsExactly(1, 2);
        assertThat(sp.size()).isEqualTo(10);
        StoredDag sd = new StoredDag("app", "g", "{}", "json");
        assertThat(sd.application()).isEqualTo("app");
        assertThat(sd.graph()).isEqualTo("g");
        assertThat(sd.definition()).isEqualTo("{}");
        assertThat(sd.format()).isEqualTo("json");
        DagCreateRequest cr = new DagCreateRequest("app", sampleDefinition());
        assertThat(cr.application()).isEqualTo("app");
        assertThat(cr.definition().graph()).isEqualTo("g");
    }

    @Test
    void runLogMessageFactories() {
        LocalDateTime now = LocalDateTime.now();
        DagRunLogMessage begin = DagRunLogMessage.begin("r1", "p1", "app", "g", "task", now, "{}");
        assertThat(begin.op()).isEqualTo(DagRunLogMessage.BEGIN);
        assertThat(begin.runId()).isEqualTo("r1");
        assertThat(begin.parentRunId()).isEqualTo("p1");
        assertThat(begin.application()).isEqualTo("app");
        assertThat(begin.graph()).isEqualTo("g");
        assertThat(begin.triggeredBy()).isEqualTo("task");
        assertThat(begin.startedAt()).isEqualTo(now);
        assertThat(begin.inputParameter()).isEqualTo("{}");

        DagRunLogMessage finish = DagRunLogMessage.finish("r1", "SUCCESS", now, 42L, 3, null, null,
                "{}");
        assertThat(finish.op()).isEqualTo(DagRunLogMessage.FINISH);
        assertThat(finish.status()).isEqualTo("SUCCESS");
        assertThat(finish.finishedAt()).isEqualTo(now);
        assertThat(finish.elapsedMs()).isEqualTo(42L);
        assertThat(finish.nodeCount()).isEqualTo(3);
        assertThat(finish.returnValue()).isEqualTo("{}");

        DagRunLogMessage node = DagRunLogMessage.node("r1", "g", "n1", 2, "SUCCESS", "in", "out",
                "exec", 7L, null);
        assertThat(node.op()).isEqualTo(DagRunLogMessage.NODE);
        assertThat(node.node()).isEqualTo("n1");
        assertThat(node.seq()).isEqualTo(2);
        assertThat(node.inputParam()).isEqualTo("in");
        assertThat(node.output()).isEqualTo("out");
        assertThat(node.executor()).isEqualTo("exec");

        DagRunLogMessage frontier = DagRunLogMessage.frontier("r1", Set.of("n1", "n2"));
        assertThat(frontier.op()).isEqualTo(DagRunLogMessage.FRONTIER);
        assertThat(frontier.frontier()).containsExactlyInAnyOrder("n1", "n2");
        // null frontier normalises to empty set
        assertThat(DagRunLogMessage.frontier("r1", null).frontier()).isEmpty();
    }
}
