package com.github.cronsmith.springapp.scheduler;

/**
 * What the leader sends an executor to run a task once. {@code executionId} correlates this dispatch
 * with the executor's {@code CompleteRequest}. {@code schedulerRepr} tags the run with the dispatching
 * scheduler node ({@code applicationName,instanceId,host:port}).
 *
 * @Description: RunRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record RunRequest(String executionId, String taskGroup, String taskName, String className,
        String beanName, String methodName, String initialParameter, int attempt, long timeout,
        String schedulerRepr) {
}
