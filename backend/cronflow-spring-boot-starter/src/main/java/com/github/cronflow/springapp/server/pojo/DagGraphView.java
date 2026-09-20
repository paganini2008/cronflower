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
package com.github.cronflow.springapp.server.pojo;

/**
 * A registered DAG as the console lists it: the owning application, the graph name, its node count
 * and the full parsed {@link DagDefinition} (nodes/edges/conditionals) the UI renders.
 *
 * @Description: DagGraphView
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagGraphView(String application, String graph, int nodeCount,
        DagDefinition definition) {

    public static DagGraphView of(DagGraph g) {
        int nodes = g.definition().nodes() == null ? 0 : g.definition().nodes().size();
        return new DagGraphView(g.application(), g.definition().graph(), nodes, g.definition());
    }
}
