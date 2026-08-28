package com.github.cronsmith.springapp.scheduler;

/**
 * 
 * Thrown when a task is looked up by an id the task manager does not know.
 * 
 * @Description: TaskDetailNotFoundException
 * @Author: Fred Feng
 * @Date: 01/06/2025
 * @Version 1.0.0
 */
public class TaskDetailNotFoundException extends TaskException {

    private static final long serialVersionUID = -5201635186837107874L;

    public TaskDetailNotFoundException() {
        super();
    }

    public TaskDetailNotFoundException(TaskId taskId) {
        super("No such task: " + taskId);
    }

    public TaskDetailNotFoundException(String msg) {
        super(msg);
    }

}
