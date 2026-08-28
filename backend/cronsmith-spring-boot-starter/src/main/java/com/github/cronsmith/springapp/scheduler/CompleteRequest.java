package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;

/**
 * The result an executor reports after a run finishes. It unblocks the dispatching node's wait, which
 * then lets the scheduler record the log and decide on a retry. Times are epoch milliseconds.
 *
 * <p>
 * Serializable because a completion may arrive at a node other than the one that dispatched (the
 * executor calls a fixed server URL), and is then broadcast over the cluster to reach the node that is
 * actually waiting.
 *
 * @Description: CompleteRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record CompleteRequest(String executionId, String taskGroup, String taskName, boolean success,
        String returnValue, String errorDetail, long firedAt, long completedAt, long elapsed,
        int attempt, String executorRepr) implements Serializable {

    private static final long serialVersionUID = 6427883901276544821L;
}
