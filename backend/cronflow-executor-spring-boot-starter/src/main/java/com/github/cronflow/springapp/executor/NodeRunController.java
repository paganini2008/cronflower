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

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.executor.pojo.NodeRunRequest;
import com.github.cronflow.springapp.executor.pojo.NodeRunResult;

/**
 * Receives node dispatches from the cronflow server and runs them <b>synchronously</b>, returning the
 * node's channel updates in the response body. A plain annotated controller, so Spring MVC or WebFlux
 * serves it. The executor is a pure callee: it never drives the workflow or calls the server back.
 *
 * @Description: NodeRunController
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@RestController
public class NodeRunController {

    /** Path this endpoint is mapped to, relative to the dispatcher (context/servlet path aside). */
    public static final String RUN_NODE_PATH = "/cronflow/nodes/run";

    private final NodeExecutionService nodeExecutionService;

    public NodeRunController(NodeExecutionService nodeExecutionService) {
        this.nodeExecutionService = nodeExecutionService;
    }

    @PostMapping(RUN_NODE_PATH)
    public NodeRunResult run(@RequestBody NodeRunRequest request) {
        return nodeExecutionService.run(request);
    }

}
