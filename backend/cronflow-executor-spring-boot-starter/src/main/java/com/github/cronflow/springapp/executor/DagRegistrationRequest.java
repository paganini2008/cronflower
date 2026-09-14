package com.github.cronflow.springapp.executor;

import java.util.List;

/**
 * The executor → server registration: this executor's callback address plus every DAG definition it
 * hosts and every task→DAG trigger binding. Sent once at startup (then heartbeats), the same shape as
 * cronsmith's executor registration.
 *
 * @Description: DagRegistrationRequest
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagRegistrationRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, List<DagDefinition> dags, List<TriggerBinding> triggers,
        Integer weight) {}
