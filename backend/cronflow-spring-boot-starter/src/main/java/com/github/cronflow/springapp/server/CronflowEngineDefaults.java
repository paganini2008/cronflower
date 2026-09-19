package com.github.cronflow.springapp.server;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns on the openspreader multi-processing switches the cronflow server needs, unless the operator
 * set them. A cronflow server <b>runs its DAGs on the openspreader engine</b>: the DAG flow travels
 * across the scheduler cluster on the process pool while each node's method runs in the executor over
 * HTTP. That engine ({@code ProcessingDag}) and its transport ({@code PoolService}) are both off by
 * default in openspreader, so without this a cronflow server would fail to start for a missing
 * {@code ProcessingDag} bean — an easy and confusing misconfiguration.
 *
 * <p>
 * The defaults are added as the <b>lowest-priority</b> property source, so anything in
 * {@code application.properties}, environment variables or the command line still wins — an operator
 * can turn either switch back off. When {@code cronflow.server.enabled=false} (a cronsmith-only
 * server) nothing is added.
 *
 * @Description: CronflowEngineDefaults
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class CronflowEngineDefaults implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "cronflowEngineDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
            SpringApplication application) {
        if ("false".equalsIgnoreCase(environment.getProperty("cronflow.server.enabled"))) {
            return;
        }
        if (environment.getPropertySources().contains(SOURCE_NAME)) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        // The DAG engine and the process pool that carries node dispatch between schedulers.
        defaults.put("spring.spreader.multiprocessing.pooling.enabled", true);
        defaults.put("spring.spreader.multiprocessing.dag.enabled", true);
        // The aggregation (MapReduce) toolkit that backs sharded (dynamic fan-out) nodes.
        defaults.put("spring.spreader.multiprocessing.aggregation.enabled", true);
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }

}
