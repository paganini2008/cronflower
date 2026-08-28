package com.github.cronsmith.springapp.scheduler;

/**
 * 
 * Thrown when a task could not be invoked, or when the task body itself failed. The cause is always
 * what the task actually threw, never a reflection wrapper.
 * 
 * @Description: TaskInvocationException
 * @Author: Fred Feng
 * @Date: 20/04/2025
 * @Version 1.0.0
 */
public class TaskInvocationException extends TaskException {

    private static final long serialVersionUID = 330511858530035307L;

    public TaskInvocationException(String msg) {
        super(msg);
    }

    public TaskInvocationException(String msg, Throwable e) {
        super(msg, e);
    }

}
