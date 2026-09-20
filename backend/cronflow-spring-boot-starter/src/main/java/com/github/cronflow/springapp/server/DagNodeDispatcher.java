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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import com.github.cronflow.springapp.server.DagExecutorRegistry.ExecutorInstance;
import com.github.cronflow.springapp.server.pojo.DagDefinition;
import com.github.cronflow.springapp.server.pojo.NodeRunRequest;
import com.github.cronflow.springapp.server.pojo.NodeRunResult;

/**
 * Dispatches one DAG node to an executor over HTTP and returns its result synchronously — the same
 * "HTTP → spring bean + method" call cronsmith makes for a task, here for a workflow node. The
 * executor is a pure callee; retry/timeout stay here (the coordinator), not on the executor.
 *
 * <p>
 * TODO: apply the node/graph timeout; reuse cronsmith's routing strategies for {@link #pick}.
 *
 * @Description: DagNodeDispatcher
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class DagNodeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DagNodeDispatcher.class);

    private final DagExecutorRegistry registry;
    private final RestClient restClient;

    public DagNodeDispatcher(DagExecutorRegistry registry, RestClient restClient) {
        this.registry = registry;
        this.restClient = restClient;
    }

    /** A node dispatch result plus which executor ran it (its node-run URL), for the run log. */
    public record Dispatched(NodeRunResult result, String executor) {}

    /** Run {@code node} of {@code graph} on some live executor hosting it, handing it {@code state}. */
    public Dispatched dispatch(String graph, DagDefinition.NodeDef node, Map<String, Object> state) {
        ExecutorInstance instance = registry.pick(graph).orElseThrow(() -> new IllegalStateException(
                "No live executor hosts graph '" + graph + "' to run node '" + node.name() + "'"));
        NodeRunRequest request = new NodeRunRequest(graph, node.name(), node.beanName(),
                node.methodName(), state);
        log.info("cronflow: dispatch {}/{} -> {}", graph, node.name(), instance.runUrl());
        NodeRunResult result = restClient.post().uri(instance.runUrl()).body(request).retrieve()
                .body(NodeRunResult.class);
        return new Dispatched(result != null ? result : new NodeRunResult(false, Map.of(),
                "empty response"), instance.runUrl());
    }

}
