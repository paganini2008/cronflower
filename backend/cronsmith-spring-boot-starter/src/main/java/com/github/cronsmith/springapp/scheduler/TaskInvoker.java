package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import com.github.cronsmith.utils.ExceptionUtils;

/**
 * 
 * Runs one occurrence of a task: the body, its retries, its timeout, the result callback, the
 * listeners and the execution log.
 * 
 * <p>
 * Always called on a worker thread, never on the thread driving the clock. Blocking here — waiting
 * on a timeout, sleeping between retries — costs a worker and nothing else.
 * 
 * @Description: TaskInvoker
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
class TaskInvoker {

    private final TaskManager taskManager;
    private final ExecutorService workerThreads;
    private final List<TaskListener> taskListeners;
    private final ErrorHandler errorHandler;

    TaskInvoker(TaskManager taskManager, ExecutorService workerThreads,
            List<TaskListener> taskListeners, ErrorHandler errorHandler) {
        this.taskManager = taskManager;
        this.workerThreads = workerThreads;
        this.taskListeners = taskListeners;
        this.errorHandler = errorHandler;
    }

    /**
     * Runs the task, retrying a failed attempt up to {@link Task#getMaxRetryCount()} times.
     * 
     * @param scheduledDateTime the fire time this run belongs to, which is what the execution log
     *        is keyed on and may be earlier than the moment work actually starts
     */
    void invoke(TaskDetail taskDetail, LocalDateTime scheduledDateTime) {
        Task task = taskDetail.getTask();
        TaskId taskId = task.getTaskId();
        String initialParameter = taskManager.getInitialParameter(taskId);
        int maxRetryCount = Math.max(0, task.getMaxRetryCount());
        long retryInterval = Math.max(0L, task.getRetryInterval());

        for (int attempt = 0; attempt <= maxRetryCount; attempt++) {
            TaskExecutionLog executionLog =
                    new TaskExecutionLog(taskId, scheduledDateTime).attempt(attempt)
                            .parameter(initialParameter);
            LocalDateTime firedDateTime = Settings.now();
            long startedAt = System.currentTimeMillis();
            executionLog.firedAt(firedDateTime);
            notifyListeners(l -> l.onTaskBegan(firedDateTime, taskDetail));

            Object returnValue = null;
            Throwable thrown = null;
            try {
                returnValue = runBody(task, initialParameter, executionLog);
                executionLog.success(true).returnValue(returnValue);
            } catch (Throwable e) {
                thrown = ExceptionUtils.getOriginalException(e);
                executionLog.error(thrown);
                errorHandler.onHandleTask(firedDateTime, thrown);
            }
            executionLog.elapsed(System.currentTimeMillis() - startedAt)
                    .completedAt(Settings.now());
            record(executionLog);

            boolean lastAttempt = thrown == null || attempt == maxRetryCount;
            if (lastAttempt) {
                handleResult(task, firedDateTime, returnValue, thrown);
                final Object finalValue = returnValue;
                final Throwable finalThrown = thrown;
                notifyListeners(
                        l -> l.onTaskEnded(firedDateTime, taskDetail, finalValue, finalThrown));
                return;
            }
            // Exponential backoff, so a task failing against an overloaded dependency stops
            // hammering it. Interrupting the worker abandons the remaining attempts.
            if (!sleepBeforeRetry(retryInterval, attempt)) {
                return;
            }
        }
    }

    /**
     * Runs the body, on this thread when there is no timeout to enforce. A timeout needs a second
     * thread to watch from, and costs a worker for the duration, so it is only paid for when the
     * task asks for one.
     */
    private Object runBody(Task task, String initialParameter, TaskExecutionLog log)
            throws Throwable {
        long timeout = task.getTimeout();
        if (timeout <= 0) {
            // Body runs on THIS thread: expose the current log so the body can enrich it.
            TaskExecutionContext.set(log);
            try {
                return task.execute(initialParameter);
            } finally {
                TaskExecutionContext.clear();
            }
        }
        // Timeout enforced from a second thread: set the context on the thread that runs the body.
        Future<Object> future = workerThreads.submit(() -> {
            TaskExecutionContext.set(log);
            try {
                return task.execute(initialParameter);
            } finally {
                TaskExecutionContext.clear();
            }
        });
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TaskInvocationException(
                    "Task " + task.getTaskId() + " timed out after " + timeout + "ms", e);
        } catch (ExecutionException e) {
            throw ExceptionUtils.getOriginalException(e);
        }
    }

    private boolean sleepBeforeRetry(long retryInterval, int attempt) {
        if (retryInterval <= 0) {
            return true;
        }
        long delay = retryInterval << Math.min(attempt, 16);
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void handleResult(Task task, LocalDateTime firedDateTime, Object returnValue,
            Throwable thrown) {
        try {
            task.handleResult(returnValue, thrown);
        } catch (Throwable e) {
            errorHandler.onHandleTaskResult(firedDateTime, e);
        }
    }

    private void record(TaskExecutionLog executionLog) {
        try {
            taskManager.recordExecution(executionLog);
        } catch (Throwable e) {
            // Losing a log row must not lose the run it describes.
            errorHandler.onHandleTaskResult(executionLog.getFiredDateTime(), e);
        }
    }

    private void notifyListeners(Consumer<TaskListener> action) {
        for (TaskListener listener : taskListeners) {
            try {
                action.accept(listener);
            } catch (Throwable e) {
                errorHandler.onHandleTaskResult(Settings.now(), e);
            }
        }
    }

}
