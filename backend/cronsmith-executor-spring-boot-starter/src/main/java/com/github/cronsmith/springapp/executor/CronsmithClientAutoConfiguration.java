package com.github.cronsmith.springapp.executor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Wires the executor side: task discovery, the run endpoint, result reporting and registration.
 *
 * <p>
 * Active in any web application — servlet or reactive — and can be switched off with
 * {@code cronsmith.client.enabled=false}. Every bean is {@code @ConditionalOnMissingBean}, so an
 * application can override any part of it.
 *
 * @Description: CronsmithClientAutoConfiguration
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "cronsmith.client", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(CronsmithClientProperties.class)
public class CronsmithClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CronsmithServerClient cronsmithServerClient(CronsmithClientProperties properties) {
        return new WebClientCronsmithServerClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskRegistry cronsmithTaskRegistry(ApplicationContext applicationContext) {
        return new TaskRegistry(applicationContext);
    }

    /** Shared holder for this executor's scheduler-assigned identity, used in the execution log. */
    @Bean
    @ConditionalOnMissingBean
    public ExecutorIdentity cronsmithExecutorIdentity() {
        return new ExecutorIdentity();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskExecutionService cronsmithTaskExecutionService(ApplicationContext applicationContext,
            CronsmithServerClient serverClient, ExecutorIdentity identity,
            CronsmithClientProperties properties) {
        return new DefaultTaskExecutionService(applicationContext, serverClient, identity,
                properties.getInvokerPoolSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public CronsmithClientController cronsmithClientController(
            TaskExecutionService taskExecutionService) {
        return new CronsmithClientController(taskExecutionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public CronsmithClientRegistrar cronsmithClientRegistrar(CronsmithClientProperties properties,
            Environment environment, TaskRegistry taskRegistry, CronsmithServerClient serverClient,
            ExecutorIdentity identity) {
        return new CronsmithClientRegistrar(properties, environment, taskRegistry, serverClient,
                identity);
    }

    /** Only when actuator's health endpoint is absent — otherwise /actuator/health is the probe. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass({"org.springframework.boot.health.actuate.endpoint.HealthEndpoint",
            "org.springframework.boot.actuate.health.HealthEndpoint"})
    public CronsmithPingController cronsmithPingController() {
        return new CronsmithPingController();
    }

}
