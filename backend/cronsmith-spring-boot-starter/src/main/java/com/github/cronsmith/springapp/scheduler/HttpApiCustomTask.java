package com.github.cronsmith.springapp.scheduler;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.ApiCallTask;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.utils.StringUtils;

/**
 * A task that calls an external HTTP API directly from the scheduler node — no executor, no client
 * SDK. It is a data-only task: the row stores no {@code task_class} / {@code task_method}. The
 * endpoint is kept in the {@code url} column as an HTTP request line
 * ({@code GET https://example.com/x HTTP/1.1}), which both names the method and marks the row as an
 * HTTP task; the request body (payload) is the initial parameter. Parsing the request line and
 * choosing when to send a body are handled by {@link ApiCallTask}; this class only performs the call
 * and knows how to build itself from a save request.
 *
 * @Description: HttpApiCustomTask
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public class HttpApiCustomTask extends ApiCallTask {

    private static final long serialVersionUID = 5820394857612034771L;
    private static final String HTTP_VERSION = "HTTP/1.1";

    public HttpApiCustomTask(Map<String, Object> record) {
        super(record);
    }

    /** Assemble a task from the fields the UI submitted: a request line plus the payload body. */
    public static HttpApiCustomTask fromRequest(TaskSaveRequest r) {
        String method = StringUtils.isNotBlank(r.httpMethod()) ? r.httpMethod().trim().toUpperCase()
                : "GET";
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("taskGroup", r.taskGroup());
        record.put("taskName", r.taskName());
        // task_class / task_method stay unset: an HTTP task carries no class of its own.
        record.put("url", requestLine(method, r.url()));
        record.put("initialParameter", r.initialParameter()); // the payload, as-is
        record.put("description", r.description());
        record.put("timeout", r.timeout());
        record.put("maxRetryCount", r.maxRetryCount());
        record.put("retryInterval", r.retryInterval());
        record.put("misfirePolicy", r.misfirePolicy());
        record.put("cron", r.cron());
        record.put("parser", r.parser());
        record.put("repeatCount", r.repeatCount());
        record.put("stopAt",
                com.github.cronsmith.utils.StringUtils.isNotBlank(r.stopAt()) ? r.stopAt().trim()
                        : null);
        return new HttpApiCustomTask(record);
    }

    private static String requestLine(String method, String url) {
        return method + " " + (url != null ? url.trim() : "") + " " + HTTP_VERSION;
    }

    /**
     * When first built from the form (no serialized schedule yet) and the schedule is an ISO-8601
     * duration such as {@code "PT1H30M"}, treat it as a fixed period; once saved, the stored bytes
     * drive it.
     */
    @Override
    public CronExpression getCronExpression() {
        if (!(record.get("cronExpression") instanceof byte[])) {
            Object cron = record.get("cron");
            if (cron != null) {
                String trimmed = cron.toString().trim();
                if (trimmed.length() > 1
                        && (trimmed.charAt(0) == 'P' || trimmed.charAt(0) == 'p')) {
                    return CRON.every(Duration.parse(trimmed));
                }
            }
        }
        return super.getCronExpression();
    }

    @Override
    protected Object sendHttpRequest(TaskId taskId, String endpoint, String httpMethod,
            Map<String, String> httpHeaders, String dataType, String data) throws IOException {
        return HttpClientUtils.sendRequest(endpoint, httpMethod, httpHeaders, dataType, data);
    }
}
