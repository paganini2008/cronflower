package com.github.cronsmith.springapp.executor;

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
                    AutoConfigurations.of(CronsmithClientAutoConfiguration.class));

    @Test
    void wiresTheExecutorBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CronsmithServerClient.class);
            assertThat(context).hasSingleBean(TaskRegistry.class);
            assertThat(context).hasSingleBean(TaskExecutionService.class);
            assertThat(context).hasSingleBean(CronsmithClientController.class);
            assertThat(context).hasSingleBean(CronsmithClientRegistrar.class);
            assertThat(context.getBean(CronsmithServerClient.class))
                    .isInstanceOf(WebClientCronsmithServerClient.class);
        });
    }

    @Test
    void usesActuatorHealthWhenPresentSoNoOwnPing() {
        // Actuator's HealthEndpoint is on the test classpath, so the fallback ping is not registered.
        runner.run(context -> assertThat(context).doesNotHaveBean(CronsmithPingController.class));
    }

    @Test
    void fallsBackToOwnPingWhenActuatorHealthAbsent() {
        runner.withClassLoader(new FilteredClassLoader(
                "org.springframework.boot.health.actuate.endpoint.HealthEndpoint",
                "org.springframework.boot.actuate.health.HealthEndpoint"))
                .run(context -> assertThat(context).hasSingleBean(CronsmithPingController.class));
    }

    @Test
    void masterSwitchDisablesEverything() {
        runner.withPropertyValues("cronsmith.client.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CronsmithClientController.class);
            assertThat(context).doesNotHaveBean(CronsmithClientRegistrar.class);
            assertThat(context).doesNotHaveBean(CronsmithServerClient.class);
        });
    }

    @Test
    void applicationCanOverrideAnyBean() {
        runner.withBean(CronsmithServerClient.class, StubClient::new).run(context -> {
            assertThat(context).hasSingleBean(CronsmithServerClient.class);
            assertThat(context.getBean(CronsmithServerClient.class)).isInstanceOf(StubClient.class);
        });
    }

    static class StubClient implements CronsmithServerClient {
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
