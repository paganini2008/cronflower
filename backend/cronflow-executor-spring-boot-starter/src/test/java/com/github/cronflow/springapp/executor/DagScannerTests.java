package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import com.github.cronflow.springapp.executor.pojo.DagDefinition;
import com.github.cronflow.springapp.executor.pojo.TriggerBinding;

/**
 * Covers the DAG scanner weaving both annotation styles: a {@code @Dag} class with {@code @DagNode}
 * methods and a {@code @Task} (which derives a trigger binding), an external {@code @DagNode} that
 * joins a graph from another bean, a {@code @DagNode} naming an unknown graph (skipped), and a
 * programmatic {@link CronflowDag} bean.
 */
class DagScannerTests {

    private GenericApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new GenericApplicationContext();
        ctx.registerBean("flowBean", FlowBean.class);
        ctx.registerBean("externalBean", ExternalBean.class);
        ctx.registerBean("ghostBean", GhostBean.class);
        ctx.registerBean("progDag", CronflowDag.class,
                () -> CronflowDag.define("prog").node("p").entry()
                        .triggeredBy("grp", "prog.kick"));
        ctx.refresh();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void weavesAnnotatedAndProgrammaticGraphs() {
        DagScanner scanner = new DagScanner(ctx, "myapp");
        DagScanner.ScanResult result = scanner.scan();

        assertThat(result.dags()).extracting(DagDefinition::graph)
                .contains("flow", "prog");

        DagDefinition flow = result.dags().stream().filter(d -> d.graph().equals("flow")).findFirst()
                .orElseThrow();
        assertThat(flow.inputs()).containsExactly("seed");
        // one built-in reducer channel and one custom-reducer channel
        assertThat(flow.channels()).extracting(DagDefinition.ChannelDef::reducer)
                .containsExactlyInAnyOrder("concatList", "myReducer");
        // nodes: start, step2 (from the @Dag class) and ext (from the external bean, pass 2)
        assertThat(flow.nodes()).extracting(DagDefinition.NodeDef::name)
                .contains("start", "step2", "ext");
        // step2 has a conditional (when/otherwise) and a shard
        assertThat(flow.conditionals()).anySatisfy(c -> assertThat(c.sources()).contains("step2"));
        assertThat(flow.shards()).anySatisfy(s -> assertThat(s.node()).isEqualTo("step2"));
        // edges include the on-success and on-failure
        assertThat(flow.edges()).anySatisfy(e -> assertThat(e.condition()).isEqualTo("ON_FAILURE"));

        // the @Task in the @Dag class derives a trigger binding to the flow graph
        assertThat(result.triggers()).extracting(TriggerBinding::graph).contains("flow", "prog");
        TriggerBinding flowTrigger = result.triggers().stream()
                .filter(t -> t.graph().equals("flow")).findFirst().orElseThrow();
        assertThat(flowTrigger.taskName()).isEqualTo("kickoff");
    }

    @Test
    void taskNameDefaultsToBeanAndMethodWhenBlank() {
        ctx.close();
        ctx = new GenericApplicationContext();
        ctx.registerBean("defaults", DefaultTaskNameBean.class);
        ctx.refresh();
        DagScanner.ScanResult result = new DagScanner(ctx, "myapp").scan();
        // @Task with no name -> group defaults to app, name to bean.method
        TriggerBinding binding = result.triggers().get(0);
        assertThat(binding.taskGroup()).isEqualTo("myapp");
        assertThat(binding.taskName()).isEqualTo("defaults.kick");
    }

    // ---- fixtures ----

    @Dag(name = "flow", inputs = {"seed"},
            channels = {@Channel(name = "steps", reducer = ChannelReducer.CONCAT_LIST),
                    @Channel(name = "custom", customReducer = "myReducer")})
    static class FlowBean {
        @DagNode(name = "start", entry = true, to = {"step2"})
        public Map<String, Object> start(DagState state) {
            return Map.of();
        }

        @DagNode(name = "step2", onFailure = {"flag"}, onComplete = {"audit"},
                when = @When(expr = "#risk > 1", to = {"review"}), otherwise = {"done"},
                shard = @Shard(input = "items", output = "results", size = 2))
        public Map<String, Object> step2() {
            return Map.of();
        }

        @Task(name = "kickoff")
        public String kick() {
            return "seed";
        }
    }

    static class ExternalBean {
        @DagNode(graph = "flow", name = "ext", to = {"step2"})
        public Map<String, Object> ext(DagState state) {
            return Map.of();
        }
    }

    static class GhostBean {
        @DagNode(graph = "no-such-graph", name = "orphan")
        public void orphan() {}
    }

    @Dag(name = "defaultsGraph")
    static class DefaultTaskNameBean {
        @DagNode(entry = true)
        public void node() {}

        @Task
        public void kick() {}
    }
}
