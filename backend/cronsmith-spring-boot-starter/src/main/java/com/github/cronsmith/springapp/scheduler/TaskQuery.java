package com.github.cronsmith.springapp.scheduler;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 
 * Filter and paging options for listing tasks. Every field is optional; an empty query matches
 * everything. Built fluently:
 * 
 * <pre>
 * TaskQuery.newQuery().group("reports").statuses(TaskStatus.SCHEDULED).limit(20).offset(40);
 * </pre>
 * 
 * @Description: TaskQuery
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TaskQuery {

    /** How many rows a query returns when no limit is given. */
    public static final int DEFAULT_LIMIT = 100;

    private String taskGroup;
    private String taskName;
    private String taskClass;
    private Set<TaskStatus> statuses = EnumSet.noneOf(TaskStatus.class);
    private int limit = DEFAULT_LIMIT;
    private int offset;

    public static TaskQuery newQuery() {
        return new TaskQuery();
    }

    /**
     * Matches the task group exactly.
     */
    public TaskQuery group(String taskGroup) {
        this.taskGroup = taskGroup;
        return this;
    }

    /**
     * Matches tasks whose name contains this fragment.
     */
    public TaskQuery name(String taskName) {
        this.taskName = taskName;
        return this;
    }

    /**
     * Matches tasks whose implementing class name contains this fragment.
     */
    public TaskQuery taskClass(String taskClass) {
        this.taskClass = taskClass;
        return this;
    }

    /**
     * Matches any of the given statuses. Calling this twice replaces the previous set.
     */
    public TaskQuery statuses(TaskStatus... statuses) {
        this.statuses = EnumSet.noneOf(TaskStatus.class);
        if (statuses != null) {
            for (TaskStatus status : statuses) {
                if (status != null) {
                    this.statuses.add(status);
                }
            }
        }
        return this;
    }

    public TaskQuery statuses(Collection<TaskStatus> statuses) {
        this.statuses = EnumSet.noneOf(TaskStatus.class);
        if (statuses != null) {
            statuses.stream().filter(s -> s != null).forEach(this.statuses::add);
        }
        return this;
    }

    /**
     * Caps the number of rows returned. A value of zero or less means no cap.
     */
    public TaskQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Skips this many rows. Negative values are treated as zero.
     */
    public TaskQuery offset(int offset) {
        this.offset = Math.max(0, offset);
        return this;
    }

    public String getTaskGroup() {
        return taskGroup;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTaskClass() {
        return taskClass;
    }

    public Set<TaskStatus> getStatuses() {
        return statuses;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return "TaskQuery [group=" + taskGroup + ", name=" + taskName + ", class=" + taskClass
                + ", statuses=" + statuses + ", limit=" + limit + ", offset=" + offset + "]";
    }

}
