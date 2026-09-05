package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.github.cronsmith.cron.CronExpression;

/**
 *
 * A unit of work with a schedule attached.
 *
 * <p>
 * Implementations are expected to be stateless with respect to a single run: the same instance is
 * reused for every occurrence, and two occurrences may overlap if one run outlives its interval.
 *
 * <p>
 * Serializable so a task definition can be forwarded to, or replicated across, cluster nodes.
 *
 * @Description: Task
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public interface Task extends Serializable {

    String DEFAULT_METHOD_NAME = "execute";

    default TaskId getTaskId() {
        return TaskId.of(getClass().getSimpleName());
    }

    default String getDescription() {
        return "";
    }

    /**
     * When this task should run. Called whenever the next fire time has to be computed, so it must
     * return an equivalent expression every time.
     */
    CronExpression getCronExpression();

    /**
     * How long a single run may take, in milliseconds. A value of zero or less means no limit.
     */
    default long getTimeout() {
        return -1L;
    }

    /**
     * How many extra attempts to make after a failed run. Zero means the failure stands.
     */
    default int getMaxRetryCount() {
        return 0;
    }

    /**
     * How long to wait before the first retry, in milliseconds. Each further retry doubles this
     * delay, so that a task failing against an overloaded dependency backs off instead of hammering
     * it.
     */
    default long getRetryInterval() {
        return 1000L;
    }

    /**
     * What to do about a run whose fire time has already passed.
     */
    default MisfirePolicy getMisfirePolicy() {
        return MisfirePolicy.FIRE_ONCE_NOW;
    }

    /**
     * For a periodic task, the total number of times it may fire before it finishes. A value of
     * zero or less means unlimited. Enforced by the task manager against the run count, so — for a
     * task that does not retry — a value of {@code n} means exactly {@code n} runs.
     */
    default int getRepeatCount() {
        return -1;
    }

    /**
     * A wall-clock instant after which the task stops firing. {@code null} means no deadline. The
     * first occurrence that would fall after this instant is not run; the task finishes instead.
     */
    default LocalDateTime getStopAt() {
        return null;
    }

    /**
     * Applies {@link #getRepeatCount()} and {@link #getStopAt()} to a freshly computed fire time.
     * Returns {@code null} — meaning "no next occurrence, the task is finished" — when the repeat cap
     * has been reached or the occurrence falls after the deadline. Shared by every task manager so the
     * two limits mean the same thing on every store.
     *
     * @param nextFiredDateTime the raw next fire time from the cron expression, possibly {@code null}
     * @param runCount how many times the task has already run
     * @param repeatCount {@link #getRepeatCount()}
     * @param stopAt {@link #getStopAt()}
     */
    static LocalDateTime capNextFiredDateTime(LocalDateTime nextFiredDateTime, long runCount,
            int repeatCount, LocalDateTime stopAt) {
        if (nextFiredDateTime == null) {
            return null;
        }
        if (repeatCount > 0 && runCount >= repeatCount) {
            return null;
        }
        if (stopAt != null && nextFiredDateTime.isAfter(stopAt)) {
            return null;
        }
        return nextFiredDateTime;
    }

    /**
     * The argument handed to {@link #execute(String)} unless the caller supplies one when the task
     * is saved.
     */
    default String getInitialParameter() {
        return "";
    }

    /**
     * The body of the task.
     */
    Object execute(String initialParameter);

    /**
     * An optional per-task completion hook: called after every run, successful or not, with exactly
     * one of the two arguments set. Runs on a worker thread and must not throw; anything it throws is
     * reported to the error handler and otherwise ignored.
     *
     * <p>
     * This is an extension point for a task written as code — a class that implements {@link Task}
     * directly and wants to react to its own outcome (update its state, fire a notification, release a
     * latch) without registering anything. The default is a no-op, which is correct and expected for
     * the data-driven built-in tasks: a task whose body is a reflective call or an HTTP request has
     * nothing local to do here.
     *
     * <p>
     * To observe the outcome of <em>every</em> task from one place (metrics, alerting), register a
     * {@link TaskListener} and use {@link TaskListener#onTaskEnded} instead — it receives the same
     * result and throwable, but as a cross-cutting observer rather than a per-task self-callback.
     */
    default void handleResult(Object result, Throwable reason) {}

}
