package com.github.cronflow.springapp.server.pojo;

import java.io.Serializable;
import java.util.List;

/**
 * Executor → server registration payload (server-side copy). Serializable so it can also travel on the
 * cluster gossip channel when the leader propagates a registration to peers.
 *
 * @Description: DagRegistrationRequest
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagRegistrationRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, List<DagDefinition> dags, List<TriggerBinding> triggers,
        Integer weight) implements Serializable {}
