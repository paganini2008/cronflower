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

/**
 * The write operations of {@code TaskManager} that are routed to the leader (and, when the storage
 * is replicated, broadcast to the other nodes). Reads never appear here — they are served locally.
 *
 * @Description: ClusterOp
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public enum ClusterOp {

    SAVE_TASK,
    REMOVE_TASK,
    COMPUTE_NEXT_FIRED,
    SET_STATUS,
    CAS_STATUS,
    RECORD_EXECUTION,
    RECORD_MISFIRE;

}
