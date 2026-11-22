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
package com.github.cronflow.springapp.pojo;

import java.util.Map;

/**
 * Server → executor node dispatch (server-side copy). The coordinator fills in the node's remote bean
 * coordinates from the stored {@link DagDefinition} and the current run state. For a script node it
 * also carries {@code language}/{@code script}/{@code datasource}; the executor branches on the
 * presence of {@code language} to run a script instead of a bean method.
 *
 * @Description: NodeRunRequest
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
public record NodeRunRequest(String graph, String node, String beanName, String methodName,
        Map<String, Object> state, String language, String script, String datasource) {

    /** Back-compatible constructor for a bean node (no script fields). */
    public NodeRunRequest(String graph, String node, String beanName, String methodName,
            Map<String, Object> state) {
        this(graph, node, beanName, methodName, state, null, null, null);
    }
}
