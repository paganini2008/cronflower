package com.github.cronsmith.springapp.executor;

/**
 * A task definition discovered from a {@link Task}-annotated method, sent to the server so it can be
 * scheduled. The server is the source of truth; this is only what the executor knows about itself.
 *
 * @Description: TaskMetadata
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record TaskMetadata(String taskGroup, String taskName, String className, String beanName,
        String methodName, String cron, String parser, String description, String initialParameter,
        long timeout, int maxRetryCount, long retryInterval, String misfirePolicy, int repeatCount,
        String stopAt) {
}
