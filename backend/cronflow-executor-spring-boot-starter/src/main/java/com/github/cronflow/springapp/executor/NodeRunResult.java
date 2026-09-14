package com.github.cronflow.springapp.executor;

import java.util.Map;

/**
 * The synchronous result of a {@link NodeRunRequest}: the channels the node changed on success, or an
 * error detail on failure. Retry and timeout are the server coordinator's concern, not the executor's.
 *
 * @Description: NodeRunResult
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record NodeRunResult(boolean ok, Map<String, Object> updates, String errorDetail) {

    public static NodeRunResult success(Map<String, Object> updates) {
        return new NodeRunResult(true, updates, null);
    }

    public static NodeRunResult failure(String errorDetail) {
        return new NodeRunResult(false, Map.of(), errorDetail);
    }

}
