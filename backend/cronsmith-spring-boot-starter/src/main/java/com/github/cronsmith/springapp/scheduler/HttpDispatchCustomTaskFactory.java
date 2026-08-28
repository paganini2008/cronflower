package com.github.cronsmith.springapp.scheduler;

import java.util.Map;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskFactory;

/**
 * Builds tasks from stored rows on the server. Registered as the global {@link TaskFactory}, so every
 * stored task — whatever the storage backend — is rebuilt here:
 *
 * <ul>
 * <li>an HTTP-API task (a row carrying a request line) becomes an {@link HttpApiCustomTask} that calls
 * the endpoint directly from the server;
 * <li>a bean task becomes an {@link HttpDispatchCustomTask} that dispatches the run to an executor,
 * since the target class lives there, not on the server.
 * </ul>
 *
 * @Description: HttpDispatchCustomTaskFactory
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class HttpDispatchCustomTaskFactory implements TaskFactory {

    @Override
    public Task createBeanReflectionTask(Map<String, Object> record) {
        return new HttpDispatchCustomTask(record);
    }

    @Override
    public Task createApiCallTask(Map<String, Object> record) {
        return new HttpApiCustomTask(record);
    }

}
