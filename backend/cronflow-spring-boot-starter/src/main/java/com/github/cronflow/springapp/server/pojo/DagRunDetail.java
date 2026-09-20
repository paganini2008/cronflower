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

import java.util.List;

/**
 * The full drill-down of one DAG run: the run itself, its node executions (in {@code seq} order), any
 * child (subgraph) runs it spawned, and — while it is still RUNNING — the nodes currently in flight
 * ({@code running}), which the console pulses on the graph. Everything reachable by a single
 * {@code runId}.
 *
 * @Description: DagRunDetail
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagRunDetail(DagRunView run, List<DagNodeView> nodes, List<DagRunView> children,
        List<String> running) {
}
