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
 * The synchronous result of a {@link NodeRunRequest}: the channels the node changed on success, or an
 * error detail on failure. Retry and timeout are the server coordinator's concern, not the executor's.
 *
 * @Description: NodeRunResult
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record NodeRunResult(boolean ok, Map<String, Object> updates, String errorDetail) {

    public static NodeRunResult success(Map<String, Object> updates) {
        return new NodeRunResult(true, updates, null);
    }

    public static NodeRunResult failure(String errorDetail) {
        return new NodeRunResult(false, Map.of(), errorDetail);
    }

}
