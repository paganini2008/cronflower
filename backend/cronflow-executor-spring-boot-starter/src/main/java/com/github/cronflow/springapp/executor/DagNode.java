/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
     * The graph this node belongs to. Leave blank for a node inside a {@code @Dag} class (it joins that
     * class's graph). Set it to a graph name to contribute a node <b>from another Spring bean</b>: the
     * method may live in any service bean, and this node runs on that bean+method — so one graph can span
     * several beans, wired together by {@link #to()} across them. The named graph's shape (channels,
     * inputs) is still declared by its {@code @Dag} class.
     */
    String graph() default "";

    /**
     * Override the bean that executes this node (defaults to the bean the method is declared on). With
     * {@link #method()} it lets a node declared in the {@code @Dag} class delegate to another service
     * bean's method; the annotated method is then a wiring placeholder and is not itself invoked.
     */
    String bean() default "";

    /** Override the method name that executes this node (defaults to the annotated method's name). */
    String method() default "";

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

    /**
     * When present (0 or 1), this node is a <b>dynamic fan-out</b>: the {@link Shard#input()} collection
     * is split and processed in parallel across the cluster, each shard dispatched to this method, and
     * the partial results gathered into {@link Shard#output()}. See {@link Shard}.
     */
    Shard[] shard() default {};

}
