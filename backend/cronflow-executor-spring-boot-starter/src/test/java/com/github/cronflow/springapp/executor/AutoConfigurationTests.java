package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

/**
 * Covers the auto-configuration: which beans are wired on a reactive web application, the
 * enabled=false master switch, and the actuator-vs-own-ping choice for the liveness probe.
 */
class AutoConfigurationTests {

    private final ReactiveWebApplicationContextRunner runner =
            new ReactiveWebApplicationContextRunner().withConfiguration(
                    AutoConfigurations.of(CronflowExecutorAutoConfiguration.class));

    @Test
    void wiresTheExecutorBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CronflowExecutorServerClient.class);
            assertThat(context).hasSingleBean(TaskRegistry.class);
            assertThat(context).hasSingleBean(TaskExecutionService.class);
            assertThat(context).hasSingleBean(CronflowExecutorController.class);
            assertThat(context).hasSingleBean(CronflowExecutorRegistrar.class);
            assertThat(context.getBean(CronflowExecutorServerClient.class))
                    .isInstanceOf(WebClientCronflowExecutorServerClient.class);
        });
    }

    @Test
    void usesActuatorHealthWhenPresentSoNoOwnPing() {
        // Actuator's HealthEndpoint is on the test classpath, so the fallback ping is not registered.
        runner.run(context -> assertThat(context).doesNotHaveBean(CronflowExecutorPingController.class));
    }

    @Test
    void fallsBackToOwnPingWhenActuatorHealthAbsent() {
        runner.withClassLoader(new FilteredClassLoader(
                "org.springframework.boot.health.actuate.endpoint.HealthEndpoint",
                "org.springframework.boot.actuate.health.HealthEndpoint"))
                .run(context -> assertThat(context).hasSingleBean(CronflowExecutorPingController.class));
    }

    @Test
    void masterSwitchDisablesEverything() {
        runner.withPropertyValues("cronflow.executor.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CronflowExecutorController.class);
            assertThat(context).doesNotHaveBean(CronflowExecutorRegistrar.class);
            assertThat(context).doesNotHaveBean(CronflowExecutorServerClient.class);
        });
    }

    @Test
    void applicationCanOverrideAnyBean() {
        runner.withBean(CronflowExecutorServerClient.class, StubClient::new).run(context -> {
            assertThat(context).hasSingleBean(CronflowExecutorServerClient.class);
            assertThat(context.getBean(CronflowExecutorServerClient.class)).isInstanceOf(StubClient.class);
        });
    }

    static class StubClient implements CronflowExecutorServerClient {
        public String register(RegistrationRequest request) {
            return "stub-instance";
        }

        public boolean heartbeat(HeartbeatRequest request) {
            return true;
        }

        public boolean complete(CompleteRequest request) {
            return true;
        }
    }

}
