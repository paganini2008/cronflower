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
package com.github.cronflow.springapp.executor;
import com.github.cronflow.springapp.executor.pojo.NodeRunRequest;
import com.github.cronflow.springapp.executor.pojo.NodeRunResult;

/**
 * Runs one DAG node on this executor. The single seam the {@link NodeRunController} calls, so an
 * application can replace how a node is resolved and invoked without touching the endpoint.
 *
 * @Description: NodeExecutionService
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface NodeExecutionService {

    /** Resolve the bean+method named in the request, invoke it with the run state, return its updates. */
    NodeRunResult run(NodeRunRequest request);

}
