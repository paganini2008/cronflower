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
package com.github.cronflow.springapp.executor;

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
 * {@code cronflow.executor.enabled=false}. Every bean is {@code @ConditionalOnMissingBean}, so an
 * application can override any part of it.
 *
 * @Description: CronflowExecutorAutoConfiguration
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "cronflow.executor", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(CronflowExecutorProperties.class)
public class CronflowExecutorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CronflowExecutorServerClient cronflowExecutorServerClient(CronflowExecutorProperties properties) {
        return new WebClientCronflowExecutorServerClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskRegistry cronflowTaskRegistry(ApplicationContext applicationContext) {
        return new TaskRegistry(applicationContext);
    }

    /** Shared holder for this executor's scheduler-assigned identity, used in the execution log. */
    @Bean
    @ConditionalOnMissingBean
    public ExecutorIdentity cronflowTaskExecutorIdentity() {
        return new ExecutorIdentity();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskExecutionService cronflowTaskExecutionService(ApplicationContext applicationContext,
            CronflowExecutorServerClient serverClient, ExecutorIdentity identity,
            CronflowExecutorProperties properties) {
        return new DefaultTaskExecutionService(applicationContext, serverClient, identity,
                properties.getInvokerPoolSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public CronflowExecutorController cronflowClientController(
            TaskExecutionService taskExecutionService) {
        return new CronflowExecutorController(taskExecutionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public CronflowExecutorRegistrar cronflowExecutorRegistrar(CronflowExecutorProperties properties,
            Environment environment, TaskRegistry taskRegistry, CronflowExecutorServerClient serverClient,
            ExecutorIdentity identity) {
        return new CronflowExecutorRegistrar(properties, environment, taskRegistry, serverClient,
                identity);
    }

    /** Only when actuator's health endpoint is absent — otherwise /actuator/health is the probe. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass({"org.springframework.boot.health.actuate.endpoint.HealthEndpoint",
            "org.springframework.boot.actuate.health.HealthEndpoint"})
    public CronflowExecutorPingController cronflowExecutorPingController() {
        return new CronflowExecutorPingController();
    }

}
