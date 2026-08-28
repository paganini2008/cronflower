package com.github.cronsmith.springapp.executor;

/**
 * A lightweight liveness ping sent on an interval after the initial registration. It carries no task
 * list — tasks are reconciled only at startup — and only keeps this executor present, and reachable,
 * in the server's in-memory executor list (e.g. after a server restart or leader change).
 *
 * @Description: HeartbeatRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record HeartbeatRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, Integer weight) {
}
