package com.github.cronflow.springapp.server.pojo;

/**
 * The reply to an executor's DAG heartbeat. {@code known} is false when the server does not have this
 * instance registered (e.g. the cluster restarted and lost its in-memory registry), which tells the
 * executor to re-register its DAG definitions rather than keep heartbeating into a void.
 *
 * @Description: DagHeartbeatResponse
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagHeartbeatResponse(boolean known) {
}
