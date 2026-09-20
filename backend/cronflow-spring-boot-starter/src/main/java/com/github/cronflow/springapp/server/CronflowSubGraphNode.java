/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
