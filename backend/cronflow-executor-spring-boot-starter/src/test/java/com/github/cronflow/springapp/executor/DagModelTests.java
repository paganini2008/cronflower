package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.github.cronflow.springapp.executor.pojo.DagDefinition;
import com.github.cronflow.springapp.executor.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.executor.pojo.DagHeartbeatResponse;
import com.github.cronflow.springapp.executor.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.executor.pojo.DagRegistrationResponse;
import com.github.cronflow.springapp.executor.pojo.NodeRunRequest;
import com.github.cronflow.springapp.executor.pojo.NodeRunResult;
import com.github.cronflow.springapp.executor.pojo.TriggerBinding;

/**
 * Covers the executor-side data model: the DAG DTOs, the immutable {@link DagState} with its typed
 * getters, the {@link ChannelReducer} enum, {@link ExecutorIdentity} and the
 * {@link CronExpressionBuilder} defaults.
 */
class DagModelTests {

    @Test
    void dagDefinitionRecords() {
        DagDefinition def = new DagDefinition("g", List.of("in"),
                List.of(new DagDefinition.ChannelDef("c", "max")),
                List.of(new DagDefinition.NodeDef("n", true, "ALL", 1, "b", "m", "")),
                List.of(new DagDefinition.EdgeDef("n", "t", "ON_SUCCESS", null)),
                List.of(new DagDefinition.ConditionalDef(List.of("n"), "predicate", null,
                        List.of("#x"), Map.of("0", List.of("t")), List.of("e"))),
                List.of(new DagDefinition.ShardDef("n", "in", "out", 3)));
        assertThat(def.graph()).isEqualTo("g");
        assertThat(def.inputs()).containsExactly("in");
        assertThat(def.channels().get(0).reducer()).isEqualTo("max");
        assertThat(def.nodes().get(0).beanName()).isEqualTo("b");
        assertThat(def.edges().get(0).condition()).isEqualTo("ON_SUCCESS");
        assertThat(def.conditionals().get(0).form()).isEqualTo("predicate");
        assertThat(def.shards().get(0).size()).isEqualTo(3);
    }

    @Test
    void registrationHeartbeatAndNodeRecords() {
        TriggerBinding tb = new TriggerBinding("grp", "name", "g");
        assertThat(tb.taskGroup()).isEqualTo("grp");
        DagRegistrationRequest reg = new DagRegistrationRequest("app", "i", "run", "health",
                List.of(), List.of(tb), 2);
        assertThat(reg.weight()).isEqualTo(2);
        assertThat(reg.triggers()).containsExactly(tb);
        assertThat(new DagRegistrationResponse("i").instanceId()).isEqualTo("i");
        DagHeartbeatRequest hb = new DagHeartbeatRequest("app", "i", "run", "health", 1);
        assertThat(hb.runUrl()).isEqualTo("run");
        assertThat(new DagHeartbeatResponse(true).known()).isTrue();
        NodeRunRequest nr = new NodeRunRequest("g", "n", "b", "m", Map.of("k", "v"));
        assertThat(nr.state()).containsEntry("k", "v");
        NodeRunResult ok = NodeRunResult.success(Map.of("o", 1));
        assertThat(ok.ok()).isTrue();
        assertThat(ok.updates()).containsEntry("o", 1);
        NodeRunResult bad = NodeRunResult.failure("boom");
        assertThat(bad.ok()).isFalse();
        assertThat(bad.errorDetail()).isEqualTo("boom");
        assertThat(bad.updates()).isEmpty();
    }

    @Test
    void dagStateTypedGetters() {
        DagState state = DagState.of(Map.of("s", "hello", "l", 5L, "i", 3, "d", 1.5, "bt", true,
                "list", List.of(1, 2), "set", Set.of("x"), "num", "7"));
        assertThat(state.contains("s")).isTrue();
        assertThat(state.contains("missing")).isFalse();
        assertThat(state.get("s")).isEqualTo("hello");
        assertThat(state.getString("s")).isEqualTo("hello");
        assertThat(state.getString("missing")).isNull();
        assertThat(state.getLong("l")).isEqualTo(5L);
        assertThat(state.getLong("num")).isEqualTo(7L); // parsed from string
        assertThat(state.getLong("missing")).isZero();
        assertThat(state.getInt("i")).isEqualTo(3);
        assertThat(state.getInt("num")).isEqualTo(7);
        assertThat(state.getInt("missing")).isZero();
        assertThat(state.getDouble("d")).isEqualTo(1.5);
        assertThat(state.getDouble("num")).isEqualTo(7.0);
        assertThat(state.getDouble("missing")).isZero();
        assertThat(state.getBoolean("bt")).isTrue();
        assertThat(state.getBoolean("missing")).isFalse();
        assertThat(state.getBoolean("s")).isFalse(); // "hello" is not a boolean
        assertThat(state.getList("list")).containsExactly(1, 2);
        assertThat(state.getList("set")).containsExactly("x");
        assertThat(state.getList("s")).containsExactly("hello"); // single value wrapped
        assertThat(state.getList("missing")).isEmpty();
        assertThat(state.asMap()).containsKey("s");
    }

    @Test
    void dagStateOfNullIsEmpty() {
        DagState state = DagState.of(null);
        assertThat(state.asMap()).isEmpty();
        assertThat(state.getString("x")).isNull();
    }

    @Test
    void channelReducerNames() {
        assertThat(ChannelReducer.LAST_WINS.reducerName()).isEqualTo("lastWins");
        assertThat(ChannelReducer.CONCAT_LIST.reducerName()).isEqualTo("concatList");
        assertThat(ChannelReducer.MAX.reducerName()).isEqualTo("max");
        assertThat(ChannelReducer.JOIN_CSV.reducerName()).isEqualTo("joinCsv");
        assertThat(ChannelReducer.values()).hasSize(14);
        assertThat(ChannelReducer.valueOf("AND").reducerName()).isEqualTo("and");
    }

    @Test
    void executorIdentityHoldsInstanceAndRepr() {
        ExecutorIdentity identity = new ExecutorIdentity();
        assertThat(identity.getInstanceId()).isNull();
        assertThat(identity.repr()).isNull();
        identity.setInstanceId("i-1");
        identity.setRepr("app,i-1,host:8080");
        assertThat(identity.getInstanceId()).isEqualTo("i-1");
        assertThat(identity.repr()).isEqualTo("app,i-1,host:8080");
    }

    @Test
    void cronExpressionBuilderDefaults() {
        CronExpressionBuilder builder = () -> "0 0 9 ? * MON";
        assertThat(builder.buildCron()).isEqualTo("0 0 9 ? * MON");
        assertThat(builder.getParser()).isEqualTo("cron");
        assertThat(builder.getRepeatCount()).isEqualTo(-1);
        assertThat(builder.getStopAt()).isNull();
        CronExpressionBuilder custom = new CronExpressionBuilder() {
            public String buildCron() {
                return "x";
            }

            public String getParser() {
                return "ycron";
            }

            public int getRepeatCount() {
                return 4;
            }

            public LocalDateTime getStopAt() {
                return LocalDateTime.MAX;
            }
        };
        assertThat(custom.getParser()).isEqualTo("ycron");
        assertThat(custom.getRepeatCount()).isEqualTo(4);
        assertThat(custom.getStopAt()).isEqualTo(LocalDateTime.MAX);
    }
}
