package com.github.cronsmith.springapp.scheduler;

/**
 * 
 * Base class for every failure raised by the task extension.
 * 
 * @Description: TaskException
 * @Author: Fred Feng
 * @Date: 16/04/2025
 * @Version 1.0.0
 */
public class TaskException extends RuntimeException {

    private static final long serialVersionUID = -8660771248670135685L;

    public TaskException() {
        super();
    }

    public TaskException(String msg) {
        super(msg);
    }

    public TaskException(String msg, Throwable e) {
        super(msg, e);
    }

}
