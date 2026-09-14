package com.github.cronflow.springapp.executor;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * One conditional branch of a {@link DagNode}: when {@link #expr()} (a SpEL predicate) is true, the
 * run routes to {@link #to()}.
 *
 * <p>
 * The expression is stored as text so the definition round-trips through {@code cf_task_dag}. Channels
 * are bound as SpEL variables of their own name (e.g. {@code #risk > 80}); a missing channel is null.
 *
 * @Description: When
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface When {

    /** A SpEL predicate over the run's channels, e.g. {@code "#risk > 80"}. */
    String expr();

    /** Target node names to run when the predicate matches. */
    String[] to();

}
