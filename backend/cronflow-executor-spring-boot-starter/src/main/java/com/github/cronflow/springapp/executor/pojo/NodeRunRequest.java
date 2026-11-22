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
package com.github.cronflow.springapp.executor.pojo;

import java.util.Map;

/**
 * A server → executor node dispatch: run {@code beanName#methodName} of graph {@code graph}'s node
 * {@code node}, handing it the current run {@code state}. Synchronous — the result comes back in the
 * HTTP response (see {@link NodeRunResult}); the executor never calls the server back.
 *
 * @Description: NodeRunRequest
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
public record NodeRunRequest(String graph, String node, String beanName, String methodName,
        Map<String, Object> state, String language, String script, String datasource) {

    /** A bean node has no {@code language}: the executor invokes the bean method. A script node carries a
     *  {@code language} and {@code script} and runs via a ScriptRunner, reading and writing channels. */
    public boolean isScript() {
        return language != null && !language.isBlank();
    }
}
