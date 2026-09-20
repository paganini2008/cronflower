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

/**
 * The server's reply to a DAG heartbeat. {@code known} is false when the server does not have this
 * instance registered (e.g. the server cluster restarted and lost its in-memory registry), telling the
 * executor to re-register its DAG definitions on the next tick.
 *
 * @Description: DagHeartbeatResponse
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagHeartbeatResponse(boolean known) {
}
