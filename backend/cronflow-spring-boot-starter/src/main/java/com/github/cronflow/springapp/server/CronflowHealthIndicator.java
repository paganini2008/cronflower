package com.github.cronflow.springapp.server;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Contributes a {@code cronflow} component to {@code /actuator/health}. It exists only when the
 * cronflow server side is on the classpath and enabled, so the console detects the DAG feature simply
 * by the presence of this component — a cronsmith-only backend has none and the UI hides everything
 * DAG. Always {@code UP} (informational): it reports how many graphs and executors are known, and does
 * not drag the overall health down.
 *
 * @Description: CronflowHealthIndicator
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class CronflowHealthIndicator implements HealthIndicator {

    private final DagExecutorRegistry registry;

    public CronflowHealthIndicator(DagExecutorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        return Health.up().withDetail("feature", "cronflow")
                .withDetail("graphs", registry.graphs().size()).build();
    }

}
