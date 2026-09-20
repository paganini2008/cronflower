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
package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry.ExecutorInstance;

/**
 * A change to the executor list, exchanged on the {@code cronsmith.executors} channel: a follower
 * forwards one to the leader, and the leader multicasts it to everyone else.
 *
 * @Description: ExecutorSyncMessage
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record ExecutorSyncMessage(Op op, ExecutorInstance instance) implements Serializable {

    public enum Op {
        UPSERT, REMOVE
    }

    public static ExecutorSyncMessage upsert(ExecutorInstance instance) {
        return new ExecutorSyncMessage(Op.UPSERT, instance);
    }

    public static ExecutorSyncMessage remove(ExecutorInstance instance) {
        return new ExecutorSyncMessage(Op.REMOVE, instance);
    }

}
