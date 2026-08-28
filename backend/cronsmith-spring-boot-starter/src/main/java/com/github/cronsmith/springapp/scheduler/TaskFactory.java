package com.github.cronsmith.springapp.scheduler;

import com.github.cronsmith.springapp.scheduler.Task;
import java.util.Map;

/**
 *
 * Turns a stored row back into a runnable task. There are exactly two kinds: one whose body is a
 * method reached reflectively, and one whose body is an HTTP request. Replace the default to control
 * how each kind finds its body — for instance by resolving a bean from a dependency-injection
 * container, or by dispatching the call to a remote executor.
 *
 * @Description: TaskFactory
 * @Author: Fred Feng
 * @Date: 08/04/2025
 * @Version 1.0.0
 */
public interface TaskFactory {

    Task createBeanReflectionTask(Map<String, Object> record);

    Task createApiCallTask(Map<String, Object> record);

}
