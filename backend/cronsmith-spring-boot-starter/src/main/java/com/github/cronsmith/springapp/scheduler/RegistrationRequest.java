package com.github.cronsmith.springapp.scheduler;

import java.util.List;

/**
 * What an executor sends on startup: who it is, where to reach it, and the tasks it can run. The URLs
 * are already fully resolved by the executor, so the leader calls them back verbatim.
 *
 * @Description: RegistrationRequest
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record RegistrationRequest(String application, String instanceId, String runUrl,
        String healthCheckUrl, List<ExecutorTaskMetadata> tasks, Integer weight) {

    /** Routing weight for WEIGHTED dispatch; absent means the default of 1. */
    public int weightOrDefault() {
        return weight != null && weight > 0 ? weight : 1;
    }
}
