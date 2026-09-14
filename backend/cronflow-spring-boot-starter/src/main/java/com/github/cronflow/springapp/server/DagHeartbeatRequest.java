package com.github.cronflow.springapp.server;

import java.io.Serializable;

/**
 * Executor → server heartbeat (server-side copy). Serializable for cluster propagation.
 *
 * @Description: DagHeartbeatRequest
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagHeartbeatRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, Integer weight) implements Serializable {}
