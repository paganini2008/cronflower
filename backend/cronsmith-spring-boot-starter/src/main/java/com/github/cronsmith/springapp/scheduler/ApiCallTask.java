package com.github.cronsmith.springapp.scheduler;

import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskInvocationException;
import java.io.IOException;
import java.util.Map;

/**
 *
 * A task whose body is a single HTTP request. Everything it needs is a value, so it can be built
 * from a form or a stored row without any code being written for it.
 *
 * <p>
 * The {@code url} column does not hold a bare URL but an HTTP request line, in the form
 * {@code "GET https://example.com/x HTTP/1.1"}: the verb and the endpoint together, with the version
 * (always {@code HTTP/1.1}) ignored. This class parses it so callers get the method and the endpoint
 * separately. The request body (payload) is the initial parameter.
 *
 * @Description: ApiCallTask
 * @Author: Fred Feng
 * @Date: 08/04/2025
 * @Version 1.0.0
 */
public abstract class ApiCallTask extends AbstractTask {

    public ApiCallTask(Map<String, Object> record) {
        super(record);
    }

    /** The raw request line, exactly as stored in the {@code url} column. */
    public String getUrl() {
        return stringOf("url", null);
    }

    /** The HTTP verb, parsed from the request line; defaults to {@code GET}. */
    public String getHttpMethod() {
        String[] parts = splitRequestLine();
        return parts.length > 0 && !parts[0].isEmpty() ? parts[0].toUpperCase() : "GET";
    }

    /** The endpoint to call, parsed from the request line; falls back to the raw value. */
    public String getEndpoint() {
        String[] parts = splitRequestLine();
        return parts.length > 1 ? parts[1] : getUrl();
    }

    private String[] splitRequestLine() {
        String line = getUrl();
        return line != null ? line.trim().split("\\s+") : new String[0];
    }

    @Override
    public Object execute(String initialParameter) {
        String httpMethod = getHttpMethod();
        String endpoint = getEndpoint();
        // A body only travels on the methods that carry one, and only when there is a payload; the
        // short "json" tag is what HttpClientUtils maps to an application/json media type.
        boolean hasBody = !"GET".equals(httpMethod) && initialParameter != null
                && !initialParameter.trim().isEmpty();
        String dataType = hasBody ? "json" : null;
        try {
            return sendHttpRequest(getTaskId(), endpoint, httpMethod, null, dataType,
                    hasBody ? initialParameter : null);
        } catch (IOException e) {
            throw new TaskInvocationException(e.getMessage(), e);
        }
    }

    protected abstract Object sendHttpRequest(TaskId taskId, String endpoint, String httpMethod,
            Map<String, String> httpHeaders, String dataType, String data) throws IOException;
}
