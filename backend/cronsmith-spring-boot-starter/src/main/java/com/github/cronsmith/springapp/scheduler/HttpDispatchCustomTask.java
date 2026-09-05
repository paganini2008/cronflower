package com.github.cronsmith.springapp.scheduler;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.BeanReflectionTask;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.utils.StringUtils;

/**
 * A task whose body lives on an executor, not here. Rebuilt from a stored row like any
 * {@code BeanReflectionTask}, but its {@code invokeTaskMethod} dispatches the run over HTTP and blocks
 * for the result, so the cronsmith {@code TaskInvoker} drives retry, timeout and logging as usual.
 *
 * <p>
 * The dispatcher is reached through {@link TaskDispatcherHolder}, not held as a field, so the task
 * object carries only data and stays serializable (and works even when an in-memory store hands the
 * same object back).
 *
 * @Description: HttpDispatchCustomTask
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class HttpDispatchCustomTask extends BeanReflectionTask {

    private static final long serialVersionUID = -1839920045567123388L;

    public HttpDispatchCustomTask(Map<String, Object> record) {
        super(record);
    }

    /** Build one from the metadata an executor registered, tagged with the executor application. */
    public static HttpDispatchCustomTask fromMetadata(ExecutorTaskMetadata metadata,
            String application) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("taskGroup", metadata.taskGroup());
        record.put("taskName", metadata.taskName());
        record.put("taskClass", metadata.className());
        record.put("taskMethod", metadata.methodName());
        record.put("beanName", metadata.beanName());
        record.put("application", application);
        record.put("url", null);
        record.put("description", metadata.description());
        record.put("initialParameter", metadata.initialParameter());
        record.put("timeout", metadata.timeout());
        record.put("maxRetryCount", metadata.maxRetryCount());
        record.put("retryInterval", metadata.retryInterval());
        record.put("misfirePolicy", metadata.misfirePolicy());
        record.put("cron", metadata.cron());
        record.put("parser", metadata.parser());
        record.put("repeatCount", metadata.repeatCount());
        record.put("stopAt", metadata.stopAt());
        return new HttpDispatchCustomTask(record);
    }

    /**
     * When a task is first built from metadata (no serialized form yet) and its schedule is an
     * ISO-8601 duration such as {@code "PT1H30M"}, treat it as a fixed period. Once saved, the stored
     * bytes drive it and the superclass reads them.
     */
    @Override
    public CronExpression getCronExpression() {
        if (!(record.get("cronExpression") instanceof byte[])) {
            String cron = stringOf("cron");
            if (isIsoDuration(cron)) {
                return CRON.every(Duration.parse(cron.trim()));
            }
        }
        return super.getCronExpression();
    }

    private static boolean isIsoDuration(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.length() > 1 && (trimmed.charAt(0) == 'P' || trimmed.charAt(0) == 'p');
    }

    public String getBeanName() {
        return stringOf("beanName");
    }

    /** The executor application to dispatch to; falls back to the task group when not stored. */
    public String getApplication() {
        String application = stringOf("application");
        return StringUtils.isNotBlank(application) ? application : stringOf("taskGroup");
    }

    @Override
    protected Object invokeTaskMethod(TaskId taskId, String taskClassName, String taskMethodName,
            String initialParameter) {
        DispatchRequest request = new DispatchRequest(getApplication(), stringOf("taskGroup"),
                stringOf("taskName"), taskClassName, getBeanName(), taskMethodName, initialParameter,
                getTimeout());
        return TaskDispatcherHolder.require().dispatchAndWait(request);
    }

    private String stringOf(String key) {
        Object value = record.get(key);
        return value != null ? value.toString() : null;
    }

}
