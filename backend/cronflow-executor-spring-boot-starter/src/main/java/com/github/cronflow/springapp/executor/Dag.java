package com.github.cronflow.springapp.executor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a Spring bean hosts the nodes of one DAG (workflow). Node methods in the bean are
 * marked with {@link DagNode}.
 *
 * <p>
 * On startup the cronflow executor weaves every {@code @Dag} bean (and every programmatic
 * {@code CronflowDag} bean) into a definition and registers it with the cronflow server, which owns
 * storage ({@code cf_task_dag}) and execution. This is the annotation-style counterpart of the
 * programmatic {@link CronflowDag} builder; both produce the same {@link DagDefinition}.
 *
 * @Description: Dag
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Dag {

    /** The graph name, unique within an application. Defaults to the simple class name when blank. */
    String name() default "";

    /** Required input channels the caller (or triggering task) must supply before the run starts. */
    String[] inputs() default {};

    /** Channels whose concurrent writes must be merged, each with a named {@link Channel#reducer()}. */
    Channel[] channels() default {};

}
