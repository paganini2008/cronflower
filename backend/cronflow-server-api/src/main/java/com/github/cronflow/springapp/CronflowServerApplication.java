package com.github.cronflow.springapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/**
 * Cronflow server: a clusterable scheduler that (as the leader) schedules tasks registered by executors
 * and dispatches them when due, plus the optional cronflow DAG engine. It is secured by login and role
 * based authorization (see the security package), and stores state in H2 (dev) or a shared database
 * (prod).
 *
 * <p>
 * Swagger UI is served at {@code /swagger-ui.html} (OpenAPI spec at {@code /v3/api-docs}); the cronsmith
 * REST controllers appear there under {@code cronsmith.server.api-prefix} and the cronflow ones under
 * {@code cronflow.server.api-prefix}. Sign in first via {@code POST /auth/login}.
 */
@OpenAPIDefinition(info = @Info(title = "Cronflow Server API", version = "1.0.0",
        description = "REST API of the Cronflow server: tasks, executors, cluster, DAG and cron tools."))
@SpringBootApplication
public class CronflowServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CronflowServerApplication.class, args);
    }

}
