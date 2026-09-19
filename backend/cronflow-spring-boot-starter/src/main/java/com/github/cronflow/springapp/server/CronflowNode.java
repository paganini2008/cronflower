package com.github.cronflow.springapp.server;

import java.util.Map;
import com.chaconneai.openspreader.dag.GraphNode;
import com.chaconneai.openspreader.dag.GraphState;
import com.chaconneai.openspreader.dag.NodeContext;
import com.github.cronflow.springapp.server.pojo.DagDefinition;
import com.github.cronflow.springapp.server.pojo.NodeRunResult;

/**
 * The one node bean the openspreader engine dispatches for every cronflow node. It carries no logic
 * of its own: {@link NodeContext} tells it which graph/node it is and, in {@code config}, the target
 * {@code bean}+{@code method} on the executor; it then dispatches that over HTTP (reusing
 * {@link DagNodeDispatcher}) and returns the channel updates the executor produced.
 *
 * <p>
 * Because it is an ordinary {@code GraphNode} bean present on every scheduler, the engine spreads a
 * run's nodes across the scheduler cluster — each scheduler runs some nodes and makes the HTTP call to
 * an executor. The DAG flows in the scheduler cluster; the target method runs in the executor.
 *
 * @Description: CronflowNode
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class CronflowNode extends GraphNode {

    private final DagNodeDispatcher dispatcher;

    public CronflowNode(DagNodeDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public Map<String, Object> execute(GraphState state) {
        // Never called: the engine always has a NodeContext for a config-driven node.
        throw new IllegalStateException("cronflow node needs its NodeContext (bean/method)");
    }

    @Override
    public Map<String, Object> execute(GraphState state, NodeContext context) {
        String bean = context.getString("bean");
        String method = context.getString("method");
        DagDefinition.NodeDef node =
                new DagDefinition.NodeDef(context.node(), false, "ALL", 0, bean, method, "");
        DagNodeDispatcher.Dispatched dispatched =
                dispatcher.dispatch(context.graph(), node, state.asMap());
        NodeRunResult result = dispatched.result();
        if (result.ok()) {
            return result.updates() == null ? Map.of() : result.updates();
        }
        throw new IllegalStateException(
                "cronflow node '" + context.node() + "' failed: " + result.errorDetail());
    }

}
