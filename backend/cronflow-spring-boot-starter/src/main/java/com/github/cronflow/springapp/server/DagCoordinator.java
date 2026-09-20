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
 * Runs a stored DAG on the server, dispatching each node to an executor over HTTP. This is the seam
 * the {@code DagTriggerTaskListener} calls: a DAG is <b>always</b> triggered by a @Task, with the
 * task's return value as the initial state.
 *
 * @Description: DagCoordinator
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface DagCoordinator {

    /**
     * Run {@code graph} from {@code initialState} (the triggering task's return value, or a payload
     * posted to the manual-trigger endpoint). Returns the run id, or {@code null} when the graph has
     * no definition or is not runnable by the engine.
     */
    String trigger(String graph, Map<String, Object> initialState, String triggeredBy);

}
