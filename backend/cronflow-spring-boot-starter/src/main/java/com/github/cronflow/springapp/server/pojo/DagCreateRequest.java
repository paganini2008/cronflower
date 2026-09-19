package com.github.cronflow.springapp.server.pojo;

import java.io.Serializable;

/**
 * A DAG authored in the console: the {@code application} whose executor hosts the node beans, and the
 * {@link DagDefinition} the user drew on the canvas. The server registers it against a live executor of
 * that application, so its nodes dispatch there exactly like an annotation-registered graph.
 *
 * @Description: DagCreateRequest
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagCreateRequest(String application, DagDefinition definition) implements Serializable {}
