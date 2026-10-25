package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.chaconneai.openspreader.dag.GraphState;
import com.chaconneai.openspreader.dag.NodeContext;
import com.github.cronflow.springapp.pojo.NodeRunResult;

/**
 * Covers the config-driven node beans the engine dispatches: {@link CronflowNode} (HTTP dispatch) and
 * {@link CronflowSubGraphNode} (nested graph), including the no-NodeContext guard and the failure path.
 */
class CronflowNodeBeansTests {

    private NodeContext ctx(Map<String, Object> config) {
        return new NodeContext("g", "run-1", "n1", config);
    }

    @Test
    void cronflowNodeDispatchesAndReturnsUpdates() throws Exception {
        DagNodeDispatcher dispatcher = mock(DagNodeDispatcher.class);
        when(dispatcher.dispatch(eq("g"), any(), any())).thenReturn(
                new DagNodeDispatcher.Dispatched(new NodeRunResult(true, Map.of("out", 1), null),
                        "exec"));
        CronflowNode node = new CronflowNode(dispatcher);
        Map<String, Object> result = node.execute(GraphState.of(Map.of("in", 1)),
                ctx(Map.of("bean", "b", "method", "m")));
        assertThat(result).containsEntry("out", 1);
    }

    @Test
    void cronflowNodeReturnsEmptyWhenUpdatesNull() throws Exception {
        DagNodeDispatcher dispatcher = mock(DagNodeDispatcher.class);
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(
                new DagNodeDispatcher.Dispatched(new NodeRunResult(true, null, null), "exec"));
        CronflowNode node = new CronflowNode(dispatcher);
        assertThat(node.execute(GraphState.of(Map.of()), ctx(Map.of("bean", "b", "method", "m"))))
                .isEmpty();
    }

    @Test
    void cronflowNodeThrowsOnFailure() {
        DagNodeDispatcher dispatcher = mock(DagNodeDispatcher.class);
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(
                new DagNodeDispatcher.Dispatched(new NodeRunResult(false, Map.of(), "kaboom"),
                        "exec"));
        CronflowNode node = new CronflowNode(dispatcher);
        assertThatThrownBy(() -> node.execute(GraphState.of(Map.of()),
                ctx(Map.of("bean", "b", "method", "m"))))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("kaboom");
    }

    @Test
    void cronflowNodeWithoutContextThrows() {
        CronflowNode node = new CronflowNode(mock(DagNodeDispatcher.class));
        assertThatThrownBy(() -> node.execute(GraphState.of(Map.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NodeContext");
    }

    @Test
    @SuppressWarnings("unchecked")
    void subGraphNodeRunsNestedGraph() throws Exception {
        SubGraphResolver resolver = mock(SubGraphResolver.class);
        when(resolver.runSubgraph(eq("child"), any(), eq("run-1"))).thenReturn(Map.of("r", 2));
        ObjectProvider<SubGraphResolver> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(resolver);
        CronflowSubGraphNode node = new CronflowSubGraphNode(provider);
        Map<String, Object> out =
                node.execute(GraphState.of(Map.of("x", 1)), ctx(Map.of("subgraph", "child")));
        assertThat(out).containsEntry("r", 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void subGraphNodeWithoutContextThrows() {
        CronflowSubGraphNode node = new CronflowSubGraphNode(mock(ObjectProvider.class));
        assertThatThrownBy(() -> node.execute(GraphState.of(Map.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NodeContext");
    }
}
