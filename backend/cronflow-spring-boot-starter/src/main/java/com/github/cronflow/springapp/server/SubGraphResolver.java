package com.github.cronflow.springapp.server;

import java.util.Map;

/**
 * Runs a nested graph (a {@code @DagNode(subgraph=...)}) from inside a parent run. Implemented by
 * {@link EngineDagRunner}; the {@code cronflowSubGraph} node calls it so a subgraph node in the parent
 * runs the child graph on the openspreader engine and folds the child's final state back in. Kept as a
 * small interface so the node bean can depend on it lazily (through an {@code ObjectProvider}) and not
 * create a cycle with the coordinator.
 *
 * @Description: SubGraphResolver
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface SubGraphResolver {

    /** Run {@code graph} as a child of {@code parentRunId}; returns the child's final channel state. */
    Map<String, Object> runSubgraph(String graph, Map<String, Object> initialState, String parentRunId);

}
