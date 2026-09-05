package com.github.cronsmith.springapp.scheduler.web;

import java.time.LocalDateTime;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.cron.CronType;
import com.github.cronsmith.springapp.scheduler.BeanReflectionTask;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.HttpApiCustomTask;
import com.github.cronsmith.springapp.scheduler.HttpDispatchCustomTask;

/**
 * A JSON-friendly view of a {@link TaskDetail}, mapped so the response never drags a live task object
 * through the serializer.
 *
 * @Description: TaskDetailView
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record TaskDetailView(String taskGroup, String taskName, String taskType, String className,
        String methodName, String beanName, String application, String url, String httpMethod,
        String httpHeaders, String cron, String parser, String description, String initialParameter,
        String status, LocalDateTime nextFiredDateTime, LocalDateTime previousFiredDateTime,
        LocalDateTime lastModified, long runCount, long failureCount, long misfireCount, long timeout,
        int maxRetryCount, long retryInterval, String misfirePolicy, int repeatCount,
        LocalDateTime stopAt) {

    public static TaskDetailView of(TaskDetail detail) {
        TaskId id = detail.getTaskId();
        String taskType = "BEAN";
        String className = null;
        String methodName = null;
        String beanName = null;
        String application = null;
        String url = null;
        String httpMethod = null;
        String httpHeaders = null;
        String cron = null;
        String parser = "cron";
        String description = null;
        String initialParameter = detail.getInitialParameter();
        long timeout = -1L;
        int maxRetryCount = 0;
        long retryInterval = 1000L;
        String misfirePolicy = null;
        int repeatCount = -1;
        LocalDateTime stopAt = null;
        try {
            Task task = detail.getTask();
            if (task instanceof HttpApiCustomTask http) {
                taskType = "HTTP";
                url = http.getEndpoint();
                httpMethod = http.getHttpMethod();
                // initialParameter already holds the payload; leave it as read from the row.
            } else if (task instanceof BeanReflectionTask bean) {
                className = bean.getTaskClassName();
                methodName = bean.getTaskMethodName();
            }
            if (task instanceof HttpDispatchCustomTask dispatch) {
                beanName = dispatch.getBeanName();
                application = dispatch.getApplication();
            }
            if (task != null) {
                description = task.getDescription();
                timeout = task.getTimeout();
                maxRetryCount = task.getMaxRetryCount();
                retryInterval = task.getRetryInterval();
                misfirePolicy = task.getMisfirePolicy() != null ? task.getMisfirePolicy().name() : null;
                repeatCount = task.getRepeatCount();
                stopAt = task.getStopAt();
                CronExpression cronExpression = task.getCronExpression();
                if (cronExpression != null) {
                    cron = cronExpression.toString();
                    parser = cronExpression.getCronType() == CronType.YCRON ? "ycron" : "cron";
                }
            }
        } catch (RuntimeException ignored) {
            // A row whose schedule or class cannot be read still lists its scheduling state below.
        }
        TaskStatus status = detail.getTaskStatus();
        return new TaskDetailView(id.getGroup(), id.getName(), taskType, className, methodName,
                beanName, application, url, httpMethod, httpHeaders, cron, parser, description,
                initialParameter, status != null ? status.name() : null, detail.getNextFiredDateTime(),
                detail.getPreviousFiredDateTime(), detail.getLastModified(), detail.getRunCount(),
                detail.getFailureCount(), detail.getMisfireCount(), timeout, maxRetryCount,
                retryInterval, misfirePolicy, repeatCount, stopAt);
    }

}
