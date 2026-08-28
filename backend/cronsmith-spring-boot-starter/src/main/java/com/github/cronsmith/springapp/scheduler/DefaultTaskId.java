package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;
import java.util.Objects;

/**
 * 
 * @Description: DefaultTaskId
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public class DefaultTaskId implements TaskId, Serializable {

    private static final long serialVersionUID = -2920712254124421978L;

    private final String group;
    private final String name;

    DefaultTaskId(String group, String name) {
        if (group == null || group.isEmpty()) {
            throw new IllegalArgumentException("Task group is required");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Task name is required");
        }
        this.group = group;
        this.name = name;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        // Deliberately narrowed to this class rather than the TaskId interface: equals and
        // hashCode have to agree, and a foreign implementation is free to hash differently.
        // Every id handed out by TaskId.of() is a DefaultTaskId, so nothing is lost.
        if (other instanceof DefaultTaskId) {
            DefaultTaskId otherId = (DefaultTaskId) other;
            return group.equals(otherId.group) && name.equals(otherId.name);
        }
        return false;
    }

    @Override
    public String toString() {
        return group + "#" + name;
    }

}
