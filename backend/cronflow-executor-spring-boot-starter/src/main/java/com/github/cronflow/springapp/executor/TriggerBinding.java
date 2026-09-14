package com.github.cronflow.springapp.executor;

/**
 * Binds a cronsmith task (identified by group + name) to a DAG. Woven from {@link DagTrigger} and
 * registered to the server, which uses it to fire {@code graph} with the task's return value when the
 * task ends.
 *
 * @Description: TriggerBinding
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record TriggerBinding(String taskGroup, String taskName, String graph) {}
