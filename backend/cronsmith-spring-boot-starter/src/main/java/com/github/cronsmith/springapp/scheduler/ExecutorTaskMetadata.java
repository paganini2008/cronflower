package com.github.cronsmith.springapp.scheduler;

/**
 * A task definition an executor reports at startup. The server persists it (saveOrUpdate) and
 * schedules it; when it fires, the leader dispatches a run back to an executor of the same
 * application.
 *
 * @Description: ExecutorTaskMetadata
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record ExecutorTaskMetadata(String taskGroup, String taskName, String className,
        String beanName, String methodName, String cron, String parser, String description,
        String initialParameter, long timeout, int maxRetryCount, long retryInterval,
        String misfirePolicy, int repeatCount, String stopAt) {

    /**
     * Back-compatible constructor: an executor that names no {@code parser} means traditional cron,
     * with no repeat cap and no deadline.
     */
    public ExecutorTaskMetadata(String taskGroup, String taskName, String className, String beanName,
            String methodName, String cron, String description, String initialParameter, long timeout,
            int maxRetryCount, long retryInterval, String misfirePolicy) {
        this(taskGroup, taskName, className, beanName, methodName, cron, "cron", description,
                initialParameter, timeout, maxRetryCount, retryInterval, misfirePolicy, -1, null);
    }
}
