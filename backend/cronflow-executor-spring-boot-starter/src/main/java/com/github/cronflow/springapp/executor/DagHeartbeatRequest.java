package com.github.cronflow.springapp.executor;

/**
 * A lightweight liveness ping (no definitions), sent on the same schedule as re-registration.
 *
 * @Description: DagHeartbeatRequest
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagHeartbeatRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, Integer weight) {}
