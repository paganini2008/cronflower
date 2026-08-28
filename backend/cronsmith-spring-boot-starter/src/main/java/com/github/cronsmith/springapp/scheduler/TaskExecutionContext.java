package com.github.cronsmith.springapp.scheduler;

import java.util.Optional;

/**
 * Exposes the log row of the run currently executing on this thread, so a task body can enrich its own
 * execution log (e.g. a remote-dispatch task recording which scheduler dispatched it and which executor
 * ran it) without changing the {@link Task#execute} signature.
 *
 * <p>
 * {@link TaskInvoker} sets the current log around the body and clears it afterwards. The body always
 * runs on the same thread the context was set on — the timeout path sets it on the worker thread it
 * submits the body to — so there is no cross-thread leakage, and concurrent runs each see their own log.
 *
 * @Description: TaskExecutionContext
 * @Author: Fred Feng
 * @Date: 28/08/2026
 * @Version 1.0.0
 */
public final class TaskExecutionContext {

    private static final ThreadLocal<TaskExecutionLog> CURRENT = new ThreadLocal<>();

    private TaskExecutionContext() {}

    static void set(TaskExecutionLog log) {
        CURRENT.set(log);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** The execution log of the run in progress on this thread, if a task body is running. */
    public static Optional<TaskExecutionLog> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
