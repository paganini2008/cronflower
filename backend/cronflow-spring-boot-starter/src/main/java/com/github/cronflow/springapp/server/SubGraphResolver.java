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

/**
 * Runs a nested graph (a {@code @DagNode(subgraph=...)}) from inside a parent run. Implemented by
 * {@link EngineDagRunner}; the {@code cronflowSubGraph} node calls it so a subgraph node in the parent
 * runs the child graph on the openspreader engine and folds the child's final state back in. Kept as a
 * small interface so the node bean can depend on it lazily (through an {@code ObjectProvider}) and not
 * create a cycle with the coordinator.
 *
 * @Description: SubGraphResolver
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface SubGraphResolver {

    /** Run {@code graph} as a child of {@code parentRunId}; returns the child's final channel state. */
    Map<String, Object> runSubgraph(String graph, Map<String, Object> initialState, String parentRunId);

}
