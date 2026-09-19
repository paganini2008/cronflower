package com.github.cronflow.springapp.server.pojo;

/**
 * A registered graph as the registry knows it: the owning application plus its parsed
 * {@link DagDefinition}. Returned by {@link DagExecutorRegistry#graphs()} for the query API.
 *
 * @Description: DagGraph
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagGraph(String application, DagDefinition definition) {
}
