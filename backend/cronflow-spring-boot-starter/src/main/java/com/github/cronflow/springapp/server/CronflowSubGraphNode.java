package com.github.cronflow.springapp.server;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import com.chaconneai.openspreader.dag.GraphNode;
import com.chaconneai.openspreader.dag.GraphState;
import com.chaconneai.openspreader.dag.NodeContext;

/**
 * The one node bean the engine dispatches for a {@code @DagNode(subgraph=...)} node: it runs the named
 * nested graph on the engine and returns the child's final state, which the parent's reducers fold in.
 *
 * <p>
 * A subgraph node is declared {@code local()} on the parent graph, so it runs on whichever scheduler is
 * coordinating that step (an inner {@link com.chaconneai.openspreader.dag.CompiledGraph} holds lambdas
 * and cannot be shipped across the network); the inner nodes then dispatch across the cluster as usual.
 *
 * @Description: CronflowSubGraphNode
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class CronflowSubGraphNode extends GraphNode {

    private final ObjectProvider<SubGraphResolver> resolver;

    public CronflowSubGraphNode(ObjectProvider<SubGraphResolver> resolver) {
        this.resolver = resolver;
    }

    @Override
    public Map<String, Object> execute(GraphState state) {
        throw new IllegalStateException("cronflow subgraph node needs its NodeContext (subgraph name)");
    }

    @Override
    public Map<String, Object> execute(GraphState state, NodeContext context) {
        String subgraph = context.getString("subgraph");
        return resolver.getObject().runSubgraph(subgraph, state.asMap(), context.runId());
    }

}
