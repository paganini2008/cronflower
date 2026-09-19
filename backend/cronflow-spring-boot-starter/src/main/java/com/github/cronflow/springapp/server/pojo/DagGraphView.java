package com.github.cronflow.springapp.server.pojo;

/**
 * A registered DAG as the console lists it: the owning application, the graph name, its node count
 * and the full parsed {@link DagDefinition} (nodes/edges/conditionals) the UI renders.
 *
 * @Description: DagGraphView
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagGraphView(String application, String graph, int nodeCount,
        DagDefinition definition) {

    public static DagGraphView of(DagGraph g) {
        int nodes = g.definition().nodes() == null ? 0 : g.definition().nodes().size();
        return new DagGraphView(g.application(), g.definition().graph(), nodes, g.definition());
    }
}
