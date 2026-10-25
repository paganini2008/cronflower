package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

/**
 * Covers the cronflow client (DAG executor) auto-configuration: the beans wired on a web application,
 * the master switch, that it does not load off the web, and that any bean can be overridden.
 */
class CronflowClientAutoConfigurationTests {

    private final ReactiveWebApplicationContextRunner webRunner =
            new ReactiveWebApplicationContextRunner().withConfiguration(
                    AutoConfigurations.of(CronflowClientAutoConfiguration.class));

    @Test
    void wiresTheClientBeans() {
        webRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(CronflowWebClient.class);
            assertThat(ctx).hasSingleBean(CronflowServerClient.class);
            assertThat(ctx.getBean(CronflowServerClient.class))
                    .isInstanceOf(WebClientCronflowServerClient.class);
            assertThat(ctx).hasSingleBean(ExecutorIdentity.class);
            assertThat(ctx).hasSingleBean(DagScanner.class);
            assertThat(ctx).hasSingleBean(NodeExecutionService.class);
            assertThat(ctx.getBean(NodeExecutionService.class))
                    .isInstanceOf(DefaultNodeExecutionService.class);
            assertThat(ctx).hasSingleBean(NodeRunController.class);
            assertThat(ctx).hasSingleBean(CronflowClientRegistrar.class);
        });
    }

    @Test
    void dagScannerUsesSpringApplicationNameWhenNoExplicitApplication() {
        webRunner.withPropertyValues("spring.application.name=orders").run(ctx -> {
            assertThat(ctx).hasSingleBean(DagScanner.class);
        });
    }

    @Test
    void masterSwitchDisablesEverything() {
        webRunner.withPropertyValues("cronflow.client.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(CronflowClientRegistrar.class);
            assertThat(ctx).doesNotHaveBean(NodeRunController.class);
        });
    }

    @Test
    void doesNotLoadOffTheWeb() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CronflowClientAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CronflowClientRegistrar.class));
    }

    @Test
    void applicationCanOverrideBeans() {
        webRunner.withBean(NodeExecutionService.class, () -> request -> null).run(ctx -> assertThat(
                ctx.getBean(NodeExecutionService.class)).isNotInstanceOf(
                        DefaultNodeExecutionService.class));
    }
}
