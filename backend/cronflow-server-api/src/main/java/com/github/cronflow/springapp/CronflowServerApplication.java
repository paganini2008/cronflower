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
package com.github.cronflow.springapp;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/**
 * Cronflow server: a clusterable scheduler that (as the leader) schedules tasks registered by executors
 * and dispatches them when due, plus the optional cronflow DAG engine. It is secured by login and role
 * based authorization (see the security package), and stores state in H2 (dev) or a shared database
 * (prod).
 *
 * <p>
 * Swagger UI is served at {@code /swagger-ui.html} (OpenAPI spec at {@code /v3/api-docs}). The API is
 * split into two groups in the UI dropdown: <b>scheduler</b> ({@code /cronflow/scheduler/**}: tasks,
 * executors, cluster, cron) and <b>dag</b> ({@code /cronflow/dag/**}: workflows and runs). Sign in first
 * via {@code POST /auth/login}.
 */
@OpenAPIDefinition(info = @Info(title = "Cronflow Server API", version = "1.0.0",
        description = "REST API of the Cronflow server: tasks, executors, cluster, DAG and cron tools."))
// Scan only this application's own components (the security package). The cronflow scheduler + DAG
// beans — including all REST controllers — come from the starter's auto-configuration, not component
// scanning, so the app class living at the root package never double-instantiates them.
@SpringBootApplication(scanBasePackages = "com.github.cronflow.springapp.security")
public class CronflowServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CronflowServerApplication.class, args);
    }

    /** Swagger UI group for the scheduler (task) endpoints: everything under {@code /cronflow/scheduler}. */
    @Bean
    public GroupedOpenApi schedulerApiGroup() {
        return GroupedOpenApi.builder().group(CronflowConstants.SWAGGER_GROUP_SCHEDULER)
                .pathsToMatch(CronflowConstants.SCHEDULER_API_PREFIX + "/**").build();
    }

    /** Swagger UI group for the DAG (workflow) endpoints: everything under {@code /cronflow/dag}. */
    @Bean
    public GroupedOpenApi dagApiGroup() {
        return GroupedOpenApi.builder().group(CronflowConstants.SWAGGER_GROUP_DAG)
                .pathsToMatch(CronflowConstants.DAG_API_PREFIX + "/**").build();
    }

}
