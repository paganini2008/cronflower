package com.github.cronsmith.springapp.scheduler;

import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.cron.CronType;
import com.github.cronsmith.springapp.scheduler.MisfirePolicy;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskException;
import com.github.cronsmith.springapp.scheduler.TaskId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 *
 * @Description: AbstractTask
 * @Author: Fred Feng
 * @Date: 08/04/2025
 * @Version 1.0.0
 */
public abstract class AbstractTask implements Task {

    protected final Map<String, Object> record;

    protected AbstractTask(Map<String, Object> record) {
        if (record == null) {
            throw new IllegalArgumentException("Task record is required");
        }
        this.record = record;
    }

    @Override
    public TaskId getTaskId() {
        return TaskId.of(stringOf("taskGroup", TaskId.DEFAULT_GROUP), stringOf("taskName", null));
    }

    @Override
    public String getDescription() {
        return stringOf("description", "");
    }

    @Override
    public long getTimeout() {
        return longOf("timeout", -1L);
    }

    @Override
    public int getMaxRetryCount() {
        return (int) longOf("maxRetryCount", 0L);
    }

    @Override
    public long getRetryInterval() {
        return longOf("retryInterval", 1000L);
    }

    @Override
    public String getInitialParameter() {
        return stringOf("initialParameter", "");
    }

    @Override
    public MisfirePolicy getMisfirePolicy() {
        Object value = record.get("misfirePolicy");
        if (value instanceof MisfirePolicy) {
            return (MisfirePolicy) value;
        }
        if (value instanceof CharSequence) {
            return MisfirePolicy.valueOf(value.toString().trim().toUpperCase());
        }
        return MisfirePolicy.FIRE_ONCE_NOW;
    }

    /**
     * Reads whichever form the schedule was stored in: an expression object, its serialized bytes,
     * the expression text, or a plain instant meaning "once, then".
     */
    @Override
    public CronExpression getCronExpression() {
        Object object = record.get("cronExpression");
        if (object instanceof CronExpression) {
            return (CronExpression) object;
        } else if (object instanceof byte[]) {
            return CronExpression.deserialize((byte[]) object);
        } else if (object instanceof LocalDateTime) {
            return CRON.atFuture((LocalDateTime) object);
        } else if (object instanceof LocalDate) {
            return CRON.atFuture((LocalDate) object);
        } else if (object instanceof LocalTime) {
            return CRON.setInterval((LocalTime) object);
        } else if (object instanceof CharSequence) {
            return CRON.parse(object.toString(), cronType());
        }
        // The 'cron' column holds the text form and is written alongside the bytes, so it is worth
        // trying before giving up.
        Object cron = record.get("cron");
        if (cron instanceof CharSequence) {
            return CRON.parse(cron.toString(), cronType());
        }
        throw new TaskException("No cron expression stored for task: " + getTaskId());
    }

    /**
     * Which cron family the stored text expression is written in, as declared by the task's
     * {@code parser} attribute ({@code "cron"} / {@code "ycron"}). Only consulted when the schedule
     * is a text expression; a schedule restored from its serialized bytes already knows its own
     * type. Defaults to {@link CronType#CRON}.
     */
    protected CronType cronType() {
        return CronType.of(stringOf("parser", "cron"));
    }

    protected String stringOf(String key, String defaultValue) {
        Object value = record.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    protected long longOf(String key, long defaultValue) {
        Object value = record.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof CharSequence) {
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return "TaskId: " + getTaskId();
    }
}
