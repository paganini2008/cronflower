package com.github.cronflow.springapp.executor;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a {@link DagNode} as a <b>dynamic fan-out</b> (sharded) step: a collection that is not known
 * until run time is split into shards, each shard is processed in parallel across the scheduler cluster
 * (each dispatched to this executor over HTTP, like any node), and the partial results are gathered
 * into one. It runs on openspreader's MapReduce, so the graph stays a single node — one box, one status.
 *
 * <p>
 * The annotated method is the <b>per-shard handler</b>: it receives this shard's items on the
 * {@link #CHANNEL} channel ({@code state.getList("__shard__")}) and returns its partial result — a
 * {@code List} (the shards are concatenated) or a {@code Map} (the shards are merged). The gathered
 * whole is written to {@link #output()}.
 *
 * @Description: Shard
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Shard {

    /** The channel each shard's items arrive on, for the per-shard handler to read. */
    String CHANNEL = "__shard__";

    /** The channel holding the full collection to split (written by an upstream node). */
    String input();

    /** The channel to write the gathered (merged/concatenated) result to. */
    String output();

    /** Target items per shard; {@code 0} lets the engine pick one shard per available node. */
    int size() default 0;

}
