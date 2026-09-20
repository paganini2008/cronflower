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
