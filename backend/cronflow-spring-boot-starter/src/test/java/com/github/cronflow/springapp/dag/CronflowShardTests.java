package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import com.chaconneai.openspreader.aggregation.Emitter;
import com.chaconneai.openspreader.aggregation.MapReduceResult;
import com.chaconneai.openspreader.aggregation.MapReduceStats;
import com.chaconneai.openspreader.aggregation.ProcessingMapReduce;
import com.chaconneai.openspreader.dag.GraphState;
import com.chaconneai.openspreader.dag.NodeContext;
import com.github.cronflow.springapp.pojo.NodeRunResult;
import com.github.cronflow.springapp.pojo.ShardPayload;

/**
 * Covers the sharded (dynamic fan-out) support: {@link CronflowShardJob} split/map/reduce and
 * {@link CronflowShardedNode} dispatch over a mocked MapReduce, including the empty, null-result and
 * failure paths.
 */
class CronflowShardTests {

    private static MapReduceStats stats() {
        return new MapReduceStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private NodeContext ctx(Map<String, Object> config) {
        return new NodeContext("g", "run", "shardNode", config);
    }

    private Map<String, Object> shardConfig() {
        return Map.of("input", "in", "output", "out", "bean", "b", "method", "m", "size", 2L);
    }

    // ---- CronflowShardJob ----

    @Test
    void splitChopsBySizeAndByNodeCount() {
        CronflowShardJob job = new CronflowShardJob(mock(DagNodeDispatcher.class));
        ShardPayload sized = new ShardPayload("g", "b", "m", "out", List.of(1, 2, 3, 4, 5), 2);
        assertThat(job.split(sized, 4)).hasSize(3); // 2+2+1
        ShardPayload byNodes = new ShardPayload("g", "b", "m", "out", List.of(1, 2, 3, 4), 0);
        assertThat(job.split(byNodes, 2)).hasSize(2); // ceil(4/2)=2 per shard -> 2 shards
        ShardPayload none = new ShardPayload("g", "b", "m", "out", null, 0);
        assertThat(job.split(none, 1)).isEmpty();
    }

    @Test
    void mapDispatchesAndEmitsPartial() {
        DagNodeDispatcher dispatcher = mock(DagNodeDispatcher.class);
        when(dispatcher.dispatch(anyString(), any(), any())).thenReturn(
                new DagNodeDispatcher.Dispatched(
                        new NodeRunResult(true, Map.of("out", List.of(9)), null), "exec"));
        CronflowShardJob job = new CronflowShardJob(dispatcher);
        List<Object> emitted = new ArrayList<>();
        Emitter<String, Object> emitter = (k, v) -> emitted.add(v);
        job.map(new ShardPayload("g", "b", "m", "out", List.of(1, 2), 2), emitter);
        assertThat(emitted).containsExactly(List.of(9));
    }

    @Test
    void mapThrowsOnShardFailure() {
        DagNodeDispatcher dispatcher = mock(DagNodeDispatcher.class);
        when(dispatcher.dispatch(anyString(), any(), any())).thenReturn(
                new DagNodeDispatcher.Dispatched(new NodeRunResult(false, Map.of(), "bad"), "exec"));
        CronflowShardJob job = new CronflowShardJob(dispatcher);
        assertThatThrownBy(
                () -> job.map(new ShardPayload("g", "b", "m", "out", List.of(1), 1), (k, v) -> {
                }))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("shard");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reduceConcatenatesListsMergesMapsOrCollects() {
        CronflowShardJob job = new CronflowShardJob(mock(DagNodeDispatcher.class));
        assertThat((List) job.reduce("out", List.of(List.of(1, 2), List.of(3))))
                .containsExactly(1, 2, 3);
        assertThat((Map) job.reduce("out", List.of(Map.of("a", 1), Map.of("b", 2))))
                .containsEntry("a", 1).containsEntry("b", 2);
        assertThat((List) job.reduce("out", List.of(1, "x"))).containsExactly(1, "x");
        assertThat((List) job.reduce("out", List.of())).isEmpty();
    }

    // ---- CronflowShardedNode ----

    @Test
    void shardedNodeWithoutContextThrows() {
        CronflowShardedNode node = new CronflowShardedNode(mock(ProcessingMapReduce.class));
        assertThatThrownBy(() -> node.execute(GraphState.of(Map.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NodeContext");
    }

    @Test
    void shardedNodeMissingInputChannelThrows() {
        CronflowShardedNode node = new CronflowShardedNode(mock(ProcessingMapReduce.class));
        assertThatThrownBy(() -> node.execute(GraphState.of(Map.of()), ctx(shardConfig())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nothing has written");
    }

    @Test
    void shardedNodeEmptyInputReturnsEmptyList() throws Exception {
        CronflowShardedNode node = new CronflowShardedNode(mock(ProcessingMapReduce.class));
        Map<String, Object> out =
                node.execute(GraphState.of(Map.of("in", List.of())), ctx(shardConfig()));
        assertThat(out).containsEntry("out", List.of());
    }

    @Test
    void shardedNodeGathersResult() throws Exception {
        ProcessingMapReduce mr = mock(ProcessingMapReduce.class);
        MapReduceResult<String, Object> result =
                new MapReduceResult<>(Map.of("out", List.of(1, 2, 3)), stats());
        when(mr.<ShardPayload, String, Object, Object>submit(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        CronflowShardedNode node = new CronflowShardedNode(mr);
        Map<String, Object> out =
                node.execute(GraphState.of(Map.of("in", List.of(1, 2, 3))), ctx(shardConfig()));
        assertThat(out).containsEntry("out", List.of(1, 2, 3));
    }

    @Test
    void shardedNodeNullMergedReturnsEmpty() throws Exception {
        ProcessingMapReduce mr = mock(ProcessingMapReduce.class);
        MapReduceResult<String, Object> result =
                new MapReduceResult<>(Map.of(), stats());
        when(mr.<ShardPayload, String, Object, Object>submit(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        CronflowShardedNode node = new CronflowShardedNode(mr);
        assertThat(node.execute(GraphState.of(Map.of("in", List.of(1))), ctx(shardConfig()))).isEmpty();
    }

    @Test
    void shardedNodeExecutionFailureIsWrapped() {
        ProcessingMapReduce mr = mock(ProcessingMapReduce.class);
        when(mr.<ShardPayload, String, Object, Object>submit(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("inner boom")));
        CronflowShardedNode node = new CronflowShardedNode(mr);
        assertThatThrownBy(
                () -> node.execute(GraphState.of(Map.of("in", List.of(1))), ctx(shardConfig())))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("boom");
    }

    @Test
    void shardedNodeAcceptsArrayAndCollectionInputs() throws Exception {
        ProcessingMapReduce mr = mock(ProcessingMapReduce.class);
        when(mr.<ShardPayload, String, Object, Object>submit(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new MapReduceResult<>(Map.of("out", 1), stats())));
        CronflowShardedNode node = new CronflowShardedNode(mr);
        // a non-list, non-collection single value is wrapped into a one-element list
        assertThat(node.execute(GraphState.of(Map.of("in", "single")), ctx(shardConfig())))
                .containsEntry("out", 1);
        // a Set (Collection) input
        assertThat(node.execute(GraphState.of(Map.of("in", java.util.Set.of("a", "b"))),
                ctx(shardConfig()))).containsEntry("out", 1);
    }
}
