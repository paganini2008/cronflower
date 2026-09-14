package com.github.cronflow.springapp.server;

/**
 * A stored DAG definition as a plain runtime object, independent of any persistence tech (JPA or
 * jOOQ). {@link DagStore#loadAll()} returns these so the store contract — and the jOOQ store — never
 * depend on the JPA entities.
 *
 * @Description: StoredDag
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record StoredDag(String application, String graph, String definition, String format) {}
