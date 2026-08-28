package com.github.cronsmith.springapp.executor;

/**
 * The result the executor reports back after a run finishes. The server writes it to the execution
 * log and decides, on its own, whether to retry. Times are epoch milliseconds. {@code executorRepr}
 * tags the attempt with this executor ({@code applicationName,instanceId,host:port}).
 *
 * @Description: CompleteRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record CompleteRequest(String executionId, String taskGroup, String taskName, boolean success,
        String returnValue, String errorDetail, long firedAt, long completedAt, long elapsed,
        int attempt, String executorRepr) {
}
