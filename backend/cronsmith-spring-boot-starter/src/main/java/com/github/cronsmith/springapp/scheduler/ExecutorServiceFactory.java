package com.github.cronsmith.springapp.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import com.github.cronsmith.utils.ExecutorUtils;

/**
 * 
 * Supplies the two pools a scheduler runs on: one thread driving the clock, and a pool the task
 * bodies run on. Keeping them apart is what stops a slow task from delaying the clock.
 * 
 * @Description: ExecutorServiceFactory
 * @Author: Fred Feng
 * @Date: 06/04/2025
 * @Version 1.0.0
 */
public interface ExecutorServiceFactory {

    ScheduledExecutorService getSchedulerThreads();

    ExecutorService getWorkerThreads();

    /**
     * Whether the scheduler owns these pools and should shut them down with itself. False when the
     * pools are shared with the rest of the application.
     */
    default boolean isAutoClosed() {
        return false;
    }

    default void shutdown(ExecutorService executorService) {
        ExecutorUtils.gracefulShutdown(executorService, 60000L);
    }

}
