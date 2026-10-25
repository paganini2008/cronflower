package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.chaconneai.openspreader.dag.CompiledGraph;
import com.chaconneai.openspreader.dag.GraphListener;
import com.chaconneai.openspreader.dag.GraphState;
import com.chaconneai.openspreader.dag.NodeOutcome;
import com.chaconneai.openspreader.dag.ProcessingDag;
import com.chaconneai.openspreader.dag.Reducer;
import com.chaconneai.openspreader.dag.RunResult;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.pojo.StoredDag;

/**
 * Drives the openspreader-backed DAG runner with a mocked engine: a rich definition exercises the full
 * graph-build path (channels, triggers, edge conditions, conditionals, subgraph and sharded nodes),
 * the trigger/subgraph success and failure flows, and the LogListener frontier/node-log callbacks.
 */
class EngineDagRunnerTests {

    private final DagDefinitionCodec codec =
            new DagDefinitionCodec(new tools.jackson.databind.ObjectMapper());

    private static final class FakeStore implements DagStore {
        @Override
        public void save(String a, String g, String d, String f) {}

        @Override
        public List<StoredDag> loadAll() {
            return List.of();
        }
    }

    private DagExecutorRegistry registryWith(DagDefinition def) {
        DagExecutorRegistry registry = new DagExecutorRegistry(new FakeStore(), codec, "json", 600L);
        registry.register(new DagRegistrationRequest("app", "i1", "run", "health", List.of(def),
                List.of(), 1));
        return registry;
    }

    /** A definition touching every branch of {@code build()} while remaining engine-valid (every
     *  trigger has enough inbound edges, every node is reachable). */
    private DagDefinition richDefinition(String graph) {
        return new DagDefinition(graph, List.of("input"),
                List.of(new DagDefinition.ChannelDef("c", "max")),
                List.of(
                        new DagDefinition.NodeDef("entry", true, "ALL", 1, "b", "validate", ""),
                        new DagDefinition.NodeDef("a", false, "ALL", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("b", false, "ALL", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("joinAL", false, "AT_LEAST(2)", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("anyN", false, "ANY", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("failN", false, "ALL", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("compN", false, "ALL", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("subN", false, "ALL", 0, "b", "x", "child"),
                        new DagDefinition.NodeDef("shardedN", false, "ALL", 0, "b", "shard", ""),
                        new DagDefinition.NodeDef("badTrig", false, "WEIRD", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("blankTrig", false, "  ", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("atBadParse", false, "AT_LEAST(x)", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("atNoParen", false, "AT_LEAST", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("swSrc", false, "ALL", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("swT", false, "ALL", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("elseT", false, "ALL", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("predSrc", false, "ALL", 0, "b", "m", ""),
                        new DagDefinition.NodeDef("predT", false, "ALL", 0, "b", "m", "")),
                List.of(
                        new DagDefinition.EdgeDef("entry", "a", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("entry", "b", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("entry", "failN", "ON_FAILURE", null),
                        new DagDefinition.EdgeDef("entry", "compN", "ON_COMPLETE", null),
                        new DagDefinition.EdgeDef("entry", "ghost", null, "branchX"),
                        new DagDefinition.EdgeDef("a", "joinAL", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("a", "anyN", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("a", "swSrc", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("a", "predSrc", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("b", "joinAL", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("b", "badTrig", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("b", "blankTrig", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("b", "atBadParse", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("b", "atNoParen", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("joinAL", "subN", "ON_SUCCESS", null),
                        new DagDefinition.EdgeDef("subN", "shardedN", "ON_SUCCESS", null)),
                List.of(
                        new DagDefinition.ConditionalDef(List.of("swSrc"), "switch", "#c", null,
                                Map.of("k", List.of("swT")), List.of("elseT")),
                        new DagDefinition.ConditionalDef(List.of("predSrc"), "predicate", null,
                                List.of("#c > 1"), Map.of("0", List.of("predT")), List.of("elseT"))),
                List.of(new DagDefinition.ShardDef("shardedN", "in", "out", 2)));
    }

    private RunResult runResult(boolean failed) {
        return RunResult.of("rid", null, GraphState.of(Map.of("c", 7)),
                Map.of(), Map.of(), failed ? "fail" : null,
                failed ? new IllegalStateException("boom") : null, Map.of(), 0L, false);
    }

    private EngineDagRunner runner(ProcessingDag dagger, DagRunLog runLog, DagExecutorRegistry reg) {
        return new EngineDagRunner(dagger, reg, runLog, new tools.jackson.databind.ObjectMapper(),
                List.of(Reducer.named("customOne", (a, b) -> b), Reducer.named("  ", (a, b) -> b)));
    }

    @Test
    void triggerBuildsRichGraphRunsAndLogs() {
        DagDefinition def = richDefinition("full");
        DagExecutorRegistry reg = registryWith(def);
        ProcessingDag dagger = mock(ProcessingDag.class);
        CompiledGraph compiled = mock(CompiledGraph.class);
        when(dagger.bind(any())).thenReturn(compiled);
        when(compiled.invoke(any(GraphState.class), anyString())).thenReturn(runResult(false));
        DagRunLog runLog = mock(DagRunLog.class);

        String runId = runner(dagger, runLog, reg).trigger("full", Map.of("input", 1), "test");

        assertThat(runId).isNotNull();
        verify(runLog).begin(eq(runId), eq(null), eq("app"), eq("full"), eq("test"), any(), any());
        verify(runLog).finish(eq(runId), eq("SUCCESS"), any(), anyLong(), anyInt(), any(), any(),
                any());
    }

    @Test
    void triggerLogsFailedWhenRunFails() {
        DagDefinition def = richDefinition("failing");
        DagExecutorRegistry reg = registryWith(def);
        ProcessingDag dagger = mock(ProcessingDag.class);
        CompiledGraph compiled = mock(CompiledGraph.class);
        when(dagger.bind(any())).thenReturn(compiled);
        when(compiled.invoke(any(GraphState.class), anyString())).thenReturn(runResult(true));
        DagRunLog runLog = mock(DagRunLog.class);

        String runId = runner(dagger, runLog, reg).trigger("failing", null, "test");
        assertThat(runId).isNotNull();
        verify(runLog).finish(eq(runId), eq("FAILED"), any(), anyLong(), anyInt(), eq("fail"), any(),
                any());
    }

    @Test
    void triggerLogsFailedWhenEngineThrows() {
        DagDefinition def = richDefinition("throwing");
        DagExecutorRegistry reg = registryWith(def);
        ProcessingDag dagger = mock(ProcessingDag.class);
        CompiledGraph compiled = mock(CompiledGraph.class);
        when(dagger.bind(any())).thenReturn(compiled);
        when(compiled.invoke(any(GraphState.class), anyString()))
                .thenThrow(new RuntimeException("engine exploded"));
        DagRunLog runLog = mock(DagRunLog.class);

        String runId = runner(dagger, runLog, reg).trigger("throwing", Map.of(), "test");
        assertThat(runId).isNotNull();
        verify(runLog).finish(eq(runId), eq("FAILED"), any(), anyLong(), eq(0), any(), any(), eq(null));
    }

    @Test
    void logListenerTracksFrontierAndWritesNodeLog() {
        DagDefinition def = richDefinition("listen");
        DagExecutorRegistry reg = registryWith(def);
        ProcessingDag dagger = mock(ProcessingDag.class);
        CompiledGraph compiled = mock(CompiledGraph.class);
        when(dagger.bind(any())).thenReturn(compiled);
        when(compiled.invoke(any(GraphState.class), anyString())).thenReturn(runResult(false));
        DagRunLog runLog = mock(DagRunLog.class);
        EngineDagRunner engine = runner(dagger, runLog, reg);
        engine.trigger("listen", Map.of(), "test");

        // Capture the real compiled graph passed to bind(); its listener is the LogListener.
        ArgumentCaptor<CompiledGraph> captor = ArgumentCaptor.forClass(CompiledGraph.class);
        verify(dagger).bind(captor.capture());
        List<GraphListener> listeners = captor.getValue().listeners();
        assertThat(listeners).isNotEmpty();
        GraphListener listener = listeners.get(0);

        String runId = "run-xyz";
        listener.onStart(runId, captor.getValue(), GraphState.of(Map.of()));
        listener.onTransition(runId, "entry", "target", GraphState.of(Map.of()));
        listener.onTransition(runId, "entry", null, GraphState.of(Map.of())); // null target branch
        assertThat(engine.runningNodesOf(runId)).contains("target");
        listener.onNodeFinished(runId, NodeOutcome.success("target", Map.of("c", 1), "execHost", 5));
        listener.onNodeFinished(runId,
                NodeOutcome.failure("fail", new RuntimeException("x"), "execHost", 6));
        listener.onFailure(runResult(true), new RuntimeException("cause"));
        verify(runLog, atLeastOnce()).node(eq(runId), eq("listen"), anyString(), anyInt(), anyString(),
                any(), any(), anyString(), anyLong(), any());
        assertThat(engine.runningNodesOf("never-started")).isEmpty();
    }

    @Test
    void subgraphSuccessReturnsFinalStateAndFailureThrows() {
        DagDefinition def = richDefinition("childA");
        DagExecutorRegistry reg = registryWith(def);
        ProcessingDag dagger = mock(ProcessingDag.class);
        CompiledGraph compiled = mock(CompiledGraph.class);
        when(dagger.bind(any())).thenReturn(compiled);
        when(compiled.invoke(any(GraphState.class), anyString())).thenReturn(runResult(false));
        DagRunLog runLog = mock(DagRunLog.class);
        EngineDagRunner engine = runner(dagger, runLog, reg);
        Map<String, Object> out = engine.runSubgraph("childA", Map.of("x", 1), "parent-run");
        assertThat(out).containsEntry("c", 7);
        verify(runLog).begin(anyString(), eq("parent-run"), any(), eq("childA"), eq("subgraph"), any(),
                any());
    }

    @Test
    void subgraphFailureThrows() {
        DagDefinition def = richDefinition("childB");
        DagExecutorRegistry reg = registryWith(def);
        ProcessingDag dagger = mock(ProcessingDag.class);
        CompiledGraph compiled = mock(CompiledGraph.class);
        when(dagger.bind(any())).thenReturn(compiled);
        when(compiled.invoke(any(GraphState.class), anyString())).thenReturn(runResult(true));
        EngineDagRunner engine = runner(dagger, mock(DagRunLog.class), reg);
        assertThatThrownBy(() -> engine.runSubgraph("childB", Map.of(), "parent"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void subgraphWithNoDefinitionThrows() {
        DagExecutorRegistry reg = new DagExecutorRegistry(new FakeStore(), codec, "json", 600L);
        EngineDagRunner engine =
                runner(mock(ProcessingDag.class), mock(DagRunLog.class), reg);
        assertThatThrownBy(() -> engine.runSubgraph("missing", Map.of(), "parent"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no runnable");
    }

    @Test
    void triggerReturnsNullForUnknownOrEmptyGraph() {
        DagExecutorRegistry reg = new DagExecutorRegistry(new FakeStore(), codec, "json", 600L);
        EngineDagRunner engine = runner(mock(ProcessingDag.class), mock(DagRunLog.class), reg);
        // unknown graph -> null (and marked unsupported, so a second call short-circuits)
        assertThat(engine.trigger("missing", Map.of(), "t")).isNull();
        assertThat(engine.trigger("missing", Map.of(), "t")).isNull();

        // a graph with no nodes is not runnable
        DagDefinition empty = new DagDefinition("empty", List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        reg.register(new DagRegistrationRequest("app", "i", "run", "h", List.of(empty), List.of(), 1));
        assertThat(engine.trigger("empty", Map.of(), "t")).isNull();
    }

    @Test
    void unknownReducerFailsFast() {
        DagDefinition def = new DagDefinition("bogusReducer", List.of(),
                List.of(new DagDefinition.ChannelDef("c", "no-such-reducer")),
                List.of(new DagDefinition.NodeDef("n", true, "ALL", 0, "b", "m", "")),
                List.of(), List.of(), List.of());
        DagExecutorRegistry reg = registryWith(def);
        EngineDagRunner engine = runner(mock(ProcessingDag.class), mock(DagRunLog.class), reg);
        assertThatThrownBy(() -> engine.trigger("bogusReducer", Map.of(), "t"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unknown reducer");
    }
}
