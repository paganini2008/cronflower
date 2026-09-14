package com.github.cronflow.springapp.executor;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A state channel with a named reducer, declaring how parallel branches merge writes to it. Used
 * inside {@link Dag#channels()}.
 *
 * <p>
 * The reducer is referenced <b>by name</b> so the definition survives being written to
 * {@code cf_task_dag} and read back on the server. The eight built-ins of the DAG kernel are always
 * available: {@code lastWins}, {@code firstWins}, {@code concatList}, {@code unionSet},
 * {@code mergeMap}, {@code sumLong}, {@code sumInt}, {@code writeOnce}.
 *
 * @Description: Channel
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Channel {

    /** The channel name. */
    String name();

    /** The reducer name (a DAG-kernel built-in, or one registered on the server). */
    String reducer() default "lastWins";

}
