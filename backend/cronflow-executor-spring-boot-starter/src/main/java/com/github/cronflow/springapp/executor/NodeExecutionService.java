package com.github.cronflow.springapp.executor;

/**
 * Runs one DAG node on this executor. The single seam the {@link NodeRunController} calls, so an
 * application can replace how a node is resolved and invoked without touching the endpoint.
 *
 * @Description: NodeExecutionService
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface NodeExecutionService {

    /** Resolve the bean+method named in the request, invoke it with the run state, return its updates. */
    NodeRunResult run(NodeRunRequest request);

}
