package com.github.cronflow.springapp.executor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean method as one node of the enclosing {@link Dag}.
 *
 * <p>
 * The method reads the run's state and returns the channels it changed, i.e. its signature is
 * {@code Map<String,Object> foo(DagState state)} (a no-arg form and a {@code void} return are also
 * accepted). At run time the server dials this executor over HTTP, resolves the bean+method and
 * invokes it — the same "HTTP → spring bean + method" mechanism cronsmith uses for a task.
 *
 * <p>
 * Edges are declared here by target node name. Plain edges ({@link #to()}) carry on success; use
 * {@link #onFailure()} / {@link #onComplete()} for the other edge conditions, and {@link #when()} /
 * {@link #otherwise()} for conditional (branch) routing evaluated by SpEL on the server.
 *
 * @Description: DagNode
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DagNode {

    /** Node name, unique within the graph. Defaults to the method name when blank. */
    String name() default "";

    /**
     * When set, this node is a <b>subgraph</b>: instead of calling a bean, the server runs the named
     * DAG (itself a {@code @Dag}) as a nested workflow, seeded with the current state, and merges its
     * result channels back. The annotated method is then never invoked (it exists only for wiring).
     */
    String subgraph() default "";

    /** Whether a run starts here. At least one node in a graph must be an entry. */
    boolean entry() default false;

    /**
     * How many carrying inbound edges are needed before this node runs: {@code "ALL"} (default),
     * {@code "ANY"}, or {@code "AT_LEAST(n)"}.
     */
    String trigger() default "ALL";

    /** Extra attempts after a failed run. {@code 0} means no retry. */
    int retries() default 0;

    /** Plain (on-success) edges: the target node names to run next. */
    String[] to() default {};

    /** On-failure edges: targets to run if this node fails (the failure is then handled). */
    String[] onFailure() default {};

    /** On-complete edges: targets to run whether this node succeeded or failed. */
    String[] onComplete() default {};

    /** Conditional branches, evaluated in order; the first matching SpEL routes to its targets. */
    When[] when() default {};

    /** Where an unmatched conditional falls through to; empty means nowhere. */
    String[] otherwise() default {};

}
