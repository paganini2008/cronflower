package com.github.cronsmith.springapp.executor;

/**
 * What the server sends to run a task once. {@code executionId} correlates this dispatch with the
 * {@link CompleteRequest} the executor sends back.
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
