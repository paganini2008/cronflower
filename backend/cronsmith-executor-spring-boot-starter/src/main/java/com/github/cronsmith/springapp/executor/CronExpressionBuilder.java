package com.github.cronsmith.springapp.executor;

import java.time.LocalDateTime;

/**
 * A programmatic source of a task's schedule, named from {@link Task#builder()}.
 *
 * <p>
 * When a {@code @Task} names a builder, the builder wins over the annotation's {@code cron} /
 * {@code parser} / {@code interval} / {@code iso} / {@code repeatCount} attributes: the schedule is
 * taken entirely from the four methods below. Use it when the schedule cannot be written as a
 * compile-time constant — in particular {@link #getStopAt()}, a future instant relative to "now",
 * which no annotation attribute can express.
 *
 * <p>
 * Implement this on a Spring bean and reference it by bean name:
 *
 * <pre>
 * &#64;Component("weeklyForAMonth")
 * class WeeklyForAMonth implements CronExpressionBuilder {
 *     public String buildCron() { return "0 0 9 ? * MON"; }
 *     public int getRepeatCount() { return 4; }
 *     public LocalDateTime getStopAt() { return LocalDateTime.now().plusMonths(1); }
 * }
 *
 * &#64;Task(builder = "weeklyForAMonth")
 * public void report() { ... }
 * </pre>
 *
 * @Description: CronExpressionBuilder
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 1.0.0
 */
public interface CronExpressionBuilder {

    /**
     * The cron expression, in the syntax named by {@link #getParser()}.
     *
     * <p>
     * Recommended: build it with cronsmith's fluent {@code CronBuilder} rather than hand-writing the
     * string — e.g. {@code new CronBuilder().everyWeek().Mon().at(9, 0).toString()} — so the
     * expression is validated as you construct it and stays readable.
     */
    String buildCron();

    /** Which cron family {@link #buildCron()} is written in: {@code "cron"} or {@code "ycron"}. */
    default String getParser() {
        return "cron";
    }

    /** How many times a periodic schedule should fire before it finishes; {@code <= 0} = unlimited. */
    default int getRepeatCount() {
        return -1;
    }

    /** A wall-clock instant after which the schedule stops firing; {@code null} = no deadline. */
    default LocalDateTime getStopAt() {
        return null;
    }

}
