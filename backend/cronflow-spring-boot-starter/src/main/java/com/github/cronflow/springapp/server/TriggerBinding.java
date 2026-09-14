package com.github.cronflow.springapp.server;

import java.io.Serializable;

/**
 * A task→DAG binding received at registration: when task {@code (taskGroup, taskName)} ends, fire
 * {@code graph} with its return value as the initial state.
 *
 * @Description: TriggerBinding
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record TriggerBinding(String taskGroup, String taskName, String graph) implements Serializable {}
