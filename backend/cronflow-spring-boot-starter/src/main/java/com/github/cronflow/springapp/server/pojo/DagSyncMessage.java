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

/**
 * A cronflow registry change exchanged on the {@code cronflow.dags} gossip channel: a follower
 * forwards one to the leader, and the leader multicasts it so every node's {@link DagExecutorRegistry}
 * converges — the same pattern cronsmith uses for its executor list. Exactly one of the two payloads
 * is set.
 *
 * @Description: DagSyncMessage
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagSyncMessage(DagRegistrationRequest registration, DagHeartbeatRequest heartbeat)
        implements Serializable {

    public static DagSyncMessage register(DagRegistrationRequest registration) {
        return new DagSyncMessage(registration, null);
    }

    public static DagSyncMessage heartbeat(DagHeartbeatRequest heartbeat) {
        return new DagSyncMessage(null, heartbeat);
    }

}
