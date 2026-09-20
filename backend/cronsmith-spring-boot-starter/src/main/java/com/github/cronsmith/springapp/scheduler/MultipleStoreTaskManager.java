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

import com.github.cronsmith.springapp.scheduler.TaskManager;

/**
 * A {@link TaskManager} that is aware of the {@link StoreType} backing it — a distributed-only concern
 * the single-node core does not carry. Lets callers tell a node-local store from a shared one (e.g. to
 * decide whether group sharding is possible, or how writes replicate) without knowing the concrete
 * implementation.
 *
 * @Description: MultipleStoreTaskManager
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public interface MultipleStoreTaskManager extends TaskManager {

    /** The kind of store backing this task manager. */
    StoreType getStoreType();

}
