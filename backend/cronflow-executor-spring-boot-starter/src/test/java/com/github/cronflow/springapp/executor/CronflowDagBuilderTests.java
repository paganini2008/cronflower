package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.github.cronflow.springapp.executor.pojo.DagDefinition;
import com.github.cronflow.springapp.executor.pojo.TriggerBinding;

/**
 * Covers the programmatic {@link CronflowDag} builder: every node-level step, the flattening into a
 * {@link DagDefinition} (edges by condition, conditionals from when/otherwise) and the trigger bindings.
 */
class CronflowDagBuilderTests {

    @Test
    void buildsAFullDefinition() {
        CronflowDag dag = CronflowDag.define("order-flow")
                .input("orderId")
                .channel("steps", "concatList")
                .node("validate").bean("orderFlow").method("validate").entry().to("reserve", "charge")
                .node("charge").bean("orderFlow").method("charge").retry(2).onFailure("flag")
                .onComplete("audit").trigger("ANY")
                .node("riskScore").bean("orderFlow").method("riskScore")
                .when("#risk > 80", "humanReview").otherwise("fulfilment")
                .node("nested").subgraph("sub-flow")
                .triggeredBy("cronflow-executor", "orderFlow.kickoff");

        DagDefinition def = dag.build();
        assertThat(def.graph()).isEqualTo("order-flow");
        assertThat(def.inputs()).containsExactly("orderId");
        assertThat(def.channels()).extracting(DagDefinition.ChannelDef::name).containsExactly("steps");
        assertThat(def.nodes()).extracting(DagDefinition.NodeDef::name)
                .containsExactly("validate", "charge", "riskScore", "nested");

        DagDefinition.NodeDef validate = def.nodes().get(0);
        assertThat(validate.entry()).isTrue();
        assertThat(validate.beanName()).isEqualTo("orderFlow");
        DagDefinition.NodeDef charge = def.nodes().get(1);
        assertThat(charge.retries()).isEqualTo(2);
        assertThat(charge.trigger()).isEqualTo("ANY");
        assertThat(def.nodes().get(3).subgraph()).isEqualTo("sub-flow");

        // edges: 2 on-success from validate, 1 on-failure + 1 on-complete from charge
        assertThat(def.edges()).filteredOn(e -> e.condition().equals("ON_SUCCESS")).hasSize(2);
        assertThat(def.edges()).filteredOn(e -> e.condition().equals("ON_FAILURE")).hasSize(1);
        assertThat(def.edges()).filteredOn(e -> e.condition().equals("ON_COMPLETE")).hasSize(1);

        // one conditional woven from riskScore's when/otherwise
        assertThat(def.conditionals()).hasSize(1);
        DagDefinition.ConditionalDef cond = def.conditionals().get(0);
        assertThat(cond.sources()).containsExactly("riskScore");
        assertThat(cond.form()).isEqualTo("predicate");
        assertThat(cond.predicates()).containsExactly("#risk > 80");
        assertThat(cond.branches().get("0")).containsExactly("humanReview");
        assertThat(cond.elseTargets()).containsExactly("fulfilment");

        List<TriggerBinding> triggers = dag.triggerBindings();
        assertThat(triggers).hasSize(1);
        assertThat(triggers.get(0).taskGroup()).isEqualTo("cronflow-executor");
        assertThat(triggers.get(0).graph()).isEqualTo("order-flow");
    }

    @Test
    void nodeBuilderReExposesGraphLevelSteps() {
        // exercise the re-exposed input()/channel()/build() off a NodeBuilder
        DagDefinition def = CronflowDag.define("g")
                .node("a").entry()
                .input("seed")
                .channel("c", "lastWins")
                .node("b").to("a")
                .build();
        assertThat(def.inputs()).containsExactly("seed");
        assertThat(def.channels()).hasSize(1);
        assertThat(def.nodes()).hasSize(2);
        assertThat(def.shards()).isEmpty();
    }

    @Test
    void minimalGraphHasNoEdgesOrConditionals() {
        DagDefinition def = CronflowDag.define("g").node("only").entry().build();
        assertThat(def.nodes()).hasSize(1);
        assertThat(def.edges()).isEmpty();
        assertThat(def.conditionals()).isEmpty();
    }
}
