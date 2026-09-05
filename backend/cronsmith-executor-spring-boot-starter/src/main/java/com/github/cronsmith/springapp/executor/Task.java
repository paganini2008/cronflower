package com.github.cronsmith.springapp.executor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Marks a Spring bean method as a scheduled task.
 *
 * <p>
 * On startup the client scans every bean for methods carrying this annotation, turns each into a
 * task definition and registers it with the cronsmith server. The server owns the schedule; when a
 * task is due, the leader calls back into this application to run the annotated method.
 *
 * <p>
 * The method may take either no arguments or a single {@link String} argument. When it takes a
 * {@code String}, the task's {@code initialParameter} is passed in. A non-void return value is
 * reported back to the server and stored in the execution log.
 *
 * @Description: Task
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Task {

    /**
     * A cron expression, e.g. {@code "0 0 3 * * ?"}. Set exactly one of {@code cron}, {@code interval}
     * or {@code iso}. Validated by the server.
     */
    String cron() default "";

    /**
     * Which syntax {@link #cron()} is written in: {@code "cron"} for a traditional, month-based cron
     * expression (the default), or {@code "ycron"} for the year-based YCRON extension (schedules on
     * week-of-year / day-of-year). Only meaningful when {@code cron} is set; ignored for
     * {@code interval} and {@code iso}.
     */
    String parser() default "cron";

    /**
     * The bean name of a {@link CronExpressionBuilder}. When set, the builder is the single source of
     * the schedule — its {@link CronExpressionBuilder#buildCron() cron},
     * {@link CronExpressionBuilder#getParser() parser}, {@link CronExpressionBuilder#getRepeatCount()
     * repeatCount} and {@link CronExpressionBuilder#getStopAt() stopAt} take priority over the
     * {@code cron} / {@code parser} / {@code interval} / {@code iso} / {@code repeatCount} attributes
     * here, which are only the default when no builder is named.
     *
     * <p>
     * {@code stopAt} — a wall-clock instant in the future — has no annotation attribute on purpose: a
     * constant cannot express a value relative to "now", so a task that needs a deadline supplies it
     * through a builder (e.g. {@code getStopAt() { return LocalDateTime.now().plusDays(7); }}).
     */
    String builder() default "";

    /**
     * A fixed interval — fire every {@code interval} {@link #intervalUnit()}. Use this for a simple
     * "every N seconds/minutes/hours" schedule. {@code 0} means unset. Set exactly one of
     * {@code cron}, {@code interval} or {@code iso}.
     */
    long interval() default 0L;

    /** The unit for {@link #interval()}. */
    TimeUnit intervalUnit() default TimeUnit.SECONDS;

    /**
     * A fixed interval as an ISO-8601 duration, e.g. {@code "PT1H30M"} (every 90 minutes) or
     * {@code "PT30S"}. Unlike a cron field, any interval works. Set exactly one of {@code cron},
     * {@code interval} or {@code iso}.
     */
    String iso() default "";

    /**
     * Task group. Left blank (the default), a Spring Boot executor uses its
     * {@code spring.application.name} as the group; outside Spring Boot the core falls back to the
     * literal group {@code "default"}.
     */
    String group() default "";

    /**
     * Task name, unique within its group. Defaults to {@code beanName.methodName} when left blank.
     */
    String name() default "";

    /**
     * Human-readable description, for the console.
     */
    String description() default "";

    /**
     * The argument handed to the method. Either a plain constant, or a SpEL template of the form
     * {@code #{...}} that is evaluated on this executor at run time — so it can read beans
     * ({@code #{@myBean.value}}) or compute a fresh value on every fire. Text without {@code #{}}
     * is used verbatim.
     */
    String initialParameter() default "";

    /**
     * Per-run timeout in milliseconds; {@code -1} means no limit. Enforced by the server.
     */
    long timeout() default -1L;

    /**
     * How many times the server retries a failed run before giving up. {@code 0} means no retry.
     */
    int maxRetryCount() default 0;

    /**
     * Base delay between retries in milliseconds; the server backs off from here.
     */
    long retryInterval() default 1000L;

    /**
     * What the server does with a fire time that has already passed: {@code FIRE_ONCE_NOW},
     * {@code SKIP} or {@code FIRE_ALL}.
     */
    String misfirePolicy() default "FIRE_ONCE_NOW";

    /**
     * For a periodic task, the total number of times it should fire before it finishes. {@code -1}
     * (the default) or {@code 0} means unlimited. Overridden by {@link CronExpressionBuilder#getRepeatCount()}
     * when a {@link #builder()} is named.
     */
    int repeatCount() default -1;

}
