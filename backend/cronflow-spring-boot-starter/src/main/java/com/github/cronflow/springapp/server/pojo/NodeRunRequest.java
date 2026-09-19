package com.github.cronflow.springapp.server.pojo;

import java.util.Map;

/**
 * Server → executor node dispatch (server-side copy). The coordinator fills in the node's remote bean
 * coordinates from the stored {@link DagDefinition} and the current run state.
 *
 * @Description: NodeRunRequest
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record NodeRunRequest(String graph, String node, String beanName, String methodName,
        Map<String, Object> state) {}
