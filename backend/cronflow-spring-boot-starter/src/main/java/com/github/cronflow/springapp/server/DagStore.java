package com.github.cronflow.springapp.server;

import java.util.List;

/**
 * Durable storage for DAG definitions ({@code cf_task_dag}). Kept minimal: the registry holds the
 * parsed definitions in memory for execution; this is what survives a restart.
 *
 * @Description: DagStore
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface DagStore {

    /** Insert or update the definition for {@code (application, graph)} in the given format. */
    void save(String application, String graph, String definition, String format);

    /** Every stored definition (as JPA-free runtime objects), for warming the registry on startup. */
    List<StoredDag> loadAll();

}
