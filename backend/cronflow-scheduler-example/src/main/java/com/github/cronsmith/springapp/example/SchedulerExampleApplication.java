package com.github.cronsmith.springapp.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/**
 * Manual test bed for the cronsmith scheduler starter: a single-node cluster over an embedded H2, which
 * (as the leader) schedules tasks registered by executors and dispatches them when due.
 *
 * <p>
 * Swagger UI is served at {@code /swagger-ui.html} (OpenAPI spec at {@code /v3/api-docs}); the
 * cronsmith REST controllers appear there under {@code cronsmith.server.api-prefix}.
 */
@OpenAPIDefinition(info = @Info(title = "Cronsmith Scheduler API", version = "1.0.0",
        description = "REST API of the cronsmith scheduler: tasks, executors, cluster and cron tools."))
@SpringBootApplication
public class SchedulerExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerExampleApplication.class, args);
    }

}
