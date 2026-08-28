package com.github.cronsmith.springapp.scheduler;

/**
 * What the leader needs to run one occurrence of a task on an executor: which application to pick an
 * executor from, and the bean/method to invoke there.
 *
 * @Description: DispatchRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record DispatchRequest(String application, String taskGroup, String taskName,
        String className, String beanName, String methodName, String initialParameter,
        long timeout) {
}
