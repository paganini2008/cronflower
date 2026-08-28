package com.github.cronsmith.springapp.executor;

/**
 * Runs a dispatched task and reports the outcome back to the server.
 *
 * <p>
 * An interface so the invocation strategy can be replaced (a fake in tests, a different threading or
 * security model in production). The default is {@link DefaultTaskExecutionService}.
 *
 * @Description: TaskExecutionService
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public interface TaskExecutionService {

    /**
     * Accept a dispatch. Implementations run it off the calling thread and, when it finishes, send a
     * {@link CompleteRequest} to the server. Retry, timeout and logging are the server's job.
     */
    void dispatch(RunRequest request);

}
