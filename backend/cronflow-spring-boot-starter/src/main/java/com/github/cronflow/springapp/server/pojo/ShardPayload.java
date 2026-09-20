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

import java.io.Serializable;
import java.util.List;

/**
 * One unit of sharded work sent across the scheduler cluster by the MapReduce job: which executor
 * bean+method to call ({@code graph}/{@code bean}/{@code method}), which output channel the partial
 * lands under, the items to process, and the target shard size. It is serialised as a cluster message,
 * so it carries only data — the dispatch itself is done on the receiving node by the job bean there.
 *
 * @Description: ShardPayload
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record ShardPayload(String graph, String bean, String method, String output,
        List<Object> items, int size) implements Serializable {
}
