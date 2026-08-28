package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.DebugErrorHandler;
import com.github.cronsmith.springapp.scheduler.ErrorHandler;
import com.github.cronsmith.springapp.scheduler.LoggingErrorHandler;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskListener;

/**
 * 
 * Covers the error handlers and the no-op defaults of the listener and handler interfaces.
 * 
 * @Description: ErrorHandlerTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class ErrorHandlerTests {

    @Test
    public void testDebugErrorHandlerWritesToStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DebugErrorHandler handler = new DebugErrorHandler(new PrintStream(out, true,
                StandardCharsets.UTF_8));
        handler.onHandleScheduler(new IllegalStateException("sched"));
        handler.onHandleTask(LocalDateTime.now(), new IllegalStateException("task"));
        handler.onHandleTaskResult(LocalDateTime.now(), new IllegalStateException("result"));
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("sched"));
        assertTrue(printed.contains("task"));
        assertTrue(printed.contains("result"));
    }

    @Test
    public void testDebugErrorHandlerDefaultConstructor() {
        // Just make sure the default (stderr) constructor is usable without throwing.
        new DebugErrorHandler().onHandleScheduler(new RuntimeException("x"));
    }

    @Test
    public void testLoggingErrorHandlerDoesNotThrow() {
        LoggingErrorHandler handler = new LoggingErrorHandler();
        handler.onHandleScheduler(new RuntimeException("a"));
        handler.onHandleTask(LocalDateTime.now(), new RuntimeException("b"));
        handler.onHandleTaskResult(LocalDateTime.now(), new RuntimeException("c"));
    }

    @Test
    public void testErrorHandlerDefaultsAreNoops() {
        ErrorHandler handler = new ErrorHandler() {};
        handler.onHandleScheduler(new RuntimeException());
        handler.onHandleTask(LocalDateTime.now(), new RuntimeException());
        handler.onHandleTaskResult(LocalDateTime.now(), new RuntimeException());
    }

    @Test
    public void testTaskListenerDefaultsAreNoops() {
        TaskListener listener = new TaskListener() {};
        TaskDetail detail = null;
        listener.onTaskScheduled(LocalDateTime.now(), detail);
        listener.onTaskTriggered(LocalDateTime.now(), detail);
        listener.onTaskBegan(LocalDateTime.now(), detail);
        listener.onTaskEnded(LocalDateTime.now(), detail, null, null);
        listener.onTaskMisfired(LocalDateTime.now(), detail);
        listener.onTaskCanceled(detail);
        listener.onTaskFinished(detail);
    }

}
