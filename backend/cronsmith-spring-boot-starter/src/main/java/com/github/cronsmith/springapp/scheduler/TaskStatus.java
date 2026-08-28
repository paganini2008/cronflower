package com.github.cronsmith.springapp.scheduler;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import com.github.cronsmith.utils.EnumConstant;

/**
 * 
 * The lifecycle of a scheduled task. Transitions are not free-form: each constant declares which
 * states it may move to, and every write goes through {@link #canTransitionTo(TaskStatus)}. This is
 * what stops a task that finished on one thread from being pushed back to RUNNING by another.
 * 
 * <pre>
 *   NONE ---------&gt; STANDBY &lt;----------------+
 *                      |                     |
 *                      v                     |
 *                  SCHEDULED --&gt; RUNNING ----+
 *                      |  ^         |
 *                      v  |         v
 *                   PAUSED |     FINISHED (terminal)
 *                      |   |
 *                      +---+
 *
 *   any non-terminal --&gt; CANCELED (terminal)
 * </pre>
 * 
 * @Description: TaskStatus
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public enum TaskStatus implements EnumConstant {

    /** The task exists but has never been handed to a scheduler. */
    NONE,

    /** Stored and eligible to run, but not currently sitting in the timing wheel. */
    STANDBY,

    /** Parked in the timing wheel with a known next fire time. */
    SCHEDULED,

    /** Its body is executing right now. */
    RUNNING,

    /** Held back on request; keeps its stored state but will not fire. */
    PAUSED,

    /** The cron expression has no further fire time. Terminal. */
    FINISHED,

    /** Withdrawn on request. Terminal. */
    CANCELED;

    private static final Set<TaskStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(FINISHED, CANCELED));

    @Override
    public Object getValue() {
        return name();
    }

    @Override
    public String getRepr() {
        return name();
    }

    /**
     * Whether no further transition is possible from here.
     */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * Whether the scheduler should leave this task alone when its fire time comes round.
     */
    public boolean isUnavailable() {
        return isTerminal() || this == PAUSED;
    }

    /**
     * Whether moving to the target state is legal. Staying put is always legal, which keeps
     * idempotent writes from being rejected.
     */
    public boolean canTransitionTo(TaskStatus target) {
        if (target == null) {
            return false;
        }
        if (target == this) {
            return true;
        }
        if (isTerminal()) {
            return false;
        }
        // Withdrawal is allowed from any live state, including RUNNING: the body already running
        // is left to finish, but nothing will be scheduled after it.
        if (target == CANCELED) {
            return true;
        }
        switch (this) {
            case NONE:
                return target == STANDBY;
            case STANDBY:
                return target == SCHEDULED || target == PAUSED || target == FINISHED;
            case SCHEDULED:
                return target == RUNNING || target == STANDBY || target == PAUSED
                        || target == FINISHED;
            case RUNNING:
                return target == SCHEDULED || target == STANDBY || target == FINISHED
                        || target == PAUSED;
            case PAUSED:
                return target == STANDBY || target == SCHEDULED;
            default:
                return false;
        }
    }

    /**
     * Parses the stored representation, accepting either case.
     */
    public static TaskStatus forName(String name) {
        if (name == null) {
            return null;
        }
        return TaskStatus.valueOf(name.trim().toUpperCase());
    }

}
