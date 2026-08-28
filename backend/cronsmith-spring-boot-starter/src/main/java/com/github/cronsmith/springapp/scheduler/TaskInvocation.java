package com.github.cronsmith.springapp.scheduler;

import java.util.Map;

/**
 * 
 * The seam between a stored task definition and the code that actually runs it. Implemented by
 * anything that hosts tasks on behalf of a framework, where the object to call has to come from
 * that framework rather than from a bare constructor.
 * 
 * @Description: TaskInvocation
 * @Author: Fred Feng
 * @Date: 13/04/2025
 * @Version 1.0.0
 */
public interface TaskInvocation {

    /**
     * Resolves the task a stored row stands for.
     */
    Task retrieveTaskObject(String taskClassName, Map<String, Object> record);

    /**
     * Runs the stored task's body and returns what it produced.
     */
    Object invokeTaskMethod(TaskId taskId, String taskClassName, String taskMethodName,
            String initialParameter);

}
