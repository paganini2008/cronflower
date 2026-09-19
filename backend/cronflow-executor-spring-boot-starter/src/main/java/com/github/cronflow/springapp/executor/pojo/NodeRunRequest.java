package com.github.cronflow.springapp.executor.pojo;

import java.util.Map;

/**
 * A server → executor node dispatch: run {@code beanName#methodName} of graph {@code graph}'s node
 * {@code node}, handing it the current run {@code state}. Synchronous — the result comes back in the
 * HTTP response (see {@link NodeRunResult}); the executor never calls the server back.
 *
 * @Description: NodeRunRequest
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record NodeRunRequest(String graph, String node, String beanName, String methodName,
        Map<String, Object> state) {}
