package com.github.cronsmith.springapp.executor;

import java.util.List;

/**
 * What an executor sends to the server on startup and on every heartbeat. The URLs are fully
 * resolved — including any context path, servlet path, WebFlux base path or separate management port
 * — so the server can call them back verbatim without knowing anything about this executor's setup.
 *
 * @Description: RegistrationRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record RegistrationRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, List<TaskMetadata> tasks, Integer weight) {
}
