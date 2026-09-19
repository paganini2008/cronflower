package com.github.cronflow.springapp.server.pojo;

import java.util.Map;

/**
 * Synchronous node result returned by an executor (server-side copy).
 *
 * @Description: NodeRunResult
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record NodeRunResult(boolean ok, Map<String, Object> updates, String errorDetail) {}
