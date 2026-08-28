package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;

/**
 * 
 * Where failures go that have nowhere else to be reported: a broken scheduler tick, a task body
 * that threw, a result callback or listener that threw. Implementations must not throw themselves.
 * 
 * @Description: ErrorHandler
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public interface ErrorHandler {

    /** The clock tick itself failed. The scheduler keeps running. */
    default void onHandleScheduler(Throwable e) {}

    /** A task body threw. */
    default void onHandleTask(LocalDateTime datetime, Throwable e) {}

    /** A result callback or a listener threw. */
    default void onHandleTaskResult(LocalDateTime datetime, Throwable e) {}

}
