package com.github.cronflow.springapp.executor;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A state channel with a named reducer, declaring how parallel branches merge writes to it. Used
 * inside {@link Dag#channels()}.
 *
 * <p>
 * Pick a reducer from the {@link ChannelReducer} enum (the DAG-kernel built-ins plus cronflow's own).
 * For an application-defined reducer — a {@code Reducer} bean on the server — set {@link #customReducer()}
 * to its name instead; when both are given, {@code customReducer} wins. The chosen name travels in the
 * woven definition (so it survives {@code cf_task_dag}) and is looked up in the server's catalog.
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

    /** A built-in reducer. Ignored when {@link #customReducer()} is set. */
    ChannelReducer reducer() default ChannelReducer.LAST_WINS;

    /** Name of a custom {@code Reducer} bean on the server; overrides {@link #reducer()} when non-blank. */
    String customReducer() default "";

}
