package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;

/**
 *
 * Identifies a task by group and name. The pair is the primary key everywhere a task is stored, so
 * implementations must be immutable and must define equals and hashCode.
 *
 * <p>
 * Serializable so a task id can travel between cluster nodes.
 *
 * @Description: TaskId
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public interface TaskId extends Serializable {

    String DEFAULT_GROUP = "default";

    default String getGroup() {
        return DEFAULT_GROUP;
    }

    default String getName() {
        return "";
    }

    static TaskId of(String name) {
        return new DefaultTaskId(DEFAULT_GROUP, name);
    }

    static TaskId of(String group, String name) {
        return new DefaultTaskId(group, name);
    }
}
