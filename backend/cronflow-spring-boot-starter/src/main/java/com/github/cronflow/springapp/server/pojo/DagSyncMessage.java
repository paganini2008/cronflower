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
