package com.github.cronflow.springapp.server;

import java.util.Map;

/**
 * Runs a stored DAG on the server, dispatching each node to an executor over HTTP. This is the seam
 * the {@code DagTriggerTaskListener} calls: a DAG is <b>always</b> triggered by a @Task, with the
 * task's return value as the initial state.
 *
 * @Description: DagCoordinator
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface DagCoordinator {

    /** Run {@code graph} from {@code initialState} (the triggering task's return value). */
    void trigger(String graph, Map<String, Object> initialState, String triggeredBy);

}
