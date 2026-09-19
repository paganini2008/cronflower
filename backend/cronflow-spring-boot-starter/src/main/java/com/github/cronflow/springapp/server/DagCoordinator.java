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

    /**
     * Run {@code graph} from {@code initialState} (the triggering task's return value, or a payload
     * posted to the manual-trigger endpoint). Returns the run id, or {@code null} when the graph has
     * no definition or is not runnable by the engine.
     */
    String trigger(String graph, Map<String, Object> initialState, String triggeredBy);

}
