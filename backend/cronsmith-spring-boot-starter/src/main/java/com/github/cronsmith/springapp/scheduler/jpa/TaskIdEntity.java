package com.github.cronsmith.springapp.scheduler.jpa;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key (group + name) for {@link TaskDetailEntity}.
 *
 * @Description: TaskIdEntity
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class TaskIdEntity implements Serializable {

    private static final long serialVersionUID = 5602148873390021147L;

    private String taskGroup;
    private String taskName;

    public TaskIdEntity() {}

    public TaskIdEntity(String taskGroup, String taskName) {
        this.taskGroup = taskGroup;
        this.taskName = taskName;
    }

    public String getTaskGroup() {
        return taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TaskIdEntity)) {
            return false;
        }
        TaskIdEntity that = (TaskIdEntity) o;
        return Objects.equals(taskGroup, that.taskGroup) && Objects.equals(taskName, that.taskName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskGroup, taskName);
    }

}
