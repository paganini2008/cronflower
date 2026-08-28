package com.github.cronsmith.springapp.scheduler;

/**
 * A lightweight liveness ping from an executor, carrying no tasks. It keeps the executor present and
 * reachable in the in-memory registry.
 *
 * @Description: HeartbeatRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record HeartbeatRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, Integer weight) {

    public int weightOrDefault() {
        return weight != null && weight > 0 ? weight : 1;
    }
}
