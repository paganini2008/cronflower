package com.github.cronsmith.springapp.scheduler;

import com.github.cronsmith.utils.StringUtils;

/**
 * The write payload behind {@code POST /cronsmith/tasks}, sent by the control-plane UI. It covers
 * both task kinds: a Spring-bean task dispatched to an executor, and an HTTP-API task the scheduler
 * calls directly. {@code taskType} picks the kind; when it is absent, a non-blank {@code url} is
 * taken to mean HTTP.
 *
 * <p>
 * The executor registration path has its own DTO ({@link ExecutorTaskMetadata}); this one is only
 * for tasks authored through the UI.
 *
 * @Description: TaskSaveRequest
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public record TaskSaveRequest(String taskGroup, String taskName, String taskType, String className,
        String beanName, String methodName, String initialParameter, String url, String httpMethod,
        String httpHeaders, String dataType, String cron, String parser, String description,
        long timeout, int maxRetryCount, long retryInterval, String misfirePolicy) {

    /** Whether this describes an HTTP-API task rather than a Spring-bean task. */
    public boolean isHttp() {
        if (StringUtils.isNotBlank(taskType)) {
            return "HTTP".equalsIgnoreCase(taskType);
        }
        return StringUtils.isNotBlank(url);
    }

    /** Adapt the bean-task fields to the shape the executor dispatch path already understands. */
    public ExecutorTaskMetadata toExecutorMetadata() {
        return new ExecutorTaskMetadata(taskGroup, taskName, className, beanName, methodName, cron,
                parser, description, initialParameter, timeout, maxRetryCount, retryInterval,
                misfirePolicy);
    }
}
