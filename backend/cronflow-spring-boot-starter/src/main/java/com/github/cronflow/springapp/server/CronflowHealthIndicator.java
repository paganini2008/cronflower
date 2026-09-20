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
