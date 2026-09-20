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

import com.chaconneai.spreader.event.GossipListener;

/**
 * The scheduler lifecycle the server bootstrap drives, so it can treat either triggering model
 * uniformly. Two implementations:
 * <ul>
 * <li>{@link LeaderSchedulerLifecycle} — only the leader runs the scheduler (the default).</li>
 * <li>{@link ShardedSchedulerLifecycle} — every node runs the scheduler and triggers the task groups
 * that consistent-hash to it (opt-in {@code cronsmith.server.scheduler.sharding} over a shared
 * store).</li>
 * </ul>
 * Both react to cluster membership and leadership changes as a {@link GossipListener}.
 *
 * @Description: SchedulerLifecycle
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public interface SchedulerLifecycle extends GossipListener {

    /** Bring the scheduler(s) in line with the current cluster state. Idempotent. */
    void reconcile();

}
