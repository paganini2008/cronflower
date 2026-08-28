package com.github.cronsmith.springapp.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * Creates pools sized from the number of available processors, with named threads so that a stack
 * dump says which pool a thread belongs to. The pools are owned by the scheduler and shut down with
 * it.
 * 
 * @Description: DefaultExecutorServiceFactory
 * @Author: Fred Feng
 * @Date: 06/04/2025
 * @Version 1.0.0
 */
public class DefaultExecutorServiceFactory implements ExecutorServiceFactory {

    private final int nThreads;

    public DefaultExecutorServiceFactory() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public DefaultExecutorServiceFactory(int nThreads) {
        if (nThreads < 1) {
            throw new IllegalArgumentException("Thread count must be positive: " + nThreads);
        }
        this.nThreads = nThreads;
    }

    @Override
    public ScheduledExecutorService getSchedulerThreads() {
        // A single thread: the clock must advance in order, and a second thread would let two
        // ticks overlap.
        return Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("cronsmith-clock"));
    }

    @Override
    public ExecutorService getWorkerThreads() {
        return Executors.newFixedThreadPool(nThreads * 2, new NamedThreadFactory("cronsmith-worker"));
    }

    @Override
    public boolean isAutoClosed() {
        return true;
    }

    /**
     * 
     * @Description: NamedThreadFactory
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    static class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + counter.incrementAndGet());
            // Daemon threads: a forgotten scheduler must not be the reason a JVM refuses to exit.
            thread.setDaemon(true);
            return thread;
        }

    }

}
