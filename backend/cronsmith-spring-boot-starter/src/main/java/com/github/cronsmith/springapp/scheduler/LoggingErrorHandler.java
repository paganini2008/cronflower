package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Reports failures through SLF4J. This is the default error handler.
 * 
 * @Description: LoggingErrorHandler
 * @Author: Fred Feng
 * @Date: 14/04/2025
 * @Version 1.0.0
 */
public class LoggingErrorHandler implements ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingErrorHandler.class);

    @Override
    public void onHandleScheduler(Throwable e) {
        log.error("Scheduler tick failed: {}", e.getMessage(), e);
    }

    @Override
    public void onHandleTask(LocalDateTime datetime, Throwable e) {
        log.error("Task failed at {}: {}", datetime, e.getMessage(), e);
    }

    @Override
    public void onHandleTaskResult(LocalDateTime datetime, Throwable e) {
        log.error("Task result handling failed at {}: {}", datetime, e.getMessage(), e);
    }

}
