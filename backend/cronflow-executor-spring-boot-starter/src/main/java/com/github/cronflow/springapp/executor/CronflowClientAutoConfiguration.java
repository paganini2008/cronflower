package com.github.cronflow.springapp.executor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Wires the cronflow executor side: DAG weaving, the synchronous node-run endpoint and registration.
 *
 * <p>
 * Fully pluggable: active only in a web application and only when {@code cronflow.client.enabled} is
 * not {@code false}. An application that imports just cronsmith is untouched — none of this loads.
 * Every bean is {@code @ConditionalOnMissingBean}, so any part can be overridden.
 *
 * @Description: CronflowClientAutoConfiguration
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "cronflow.client", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(CronflowClientProperties.class)
public class CronflowClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CronflowServerClient cronflowServerClient(CronflowClientProperties properties) {
        return new WebClientCronflowServerClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutorIdentity cronflowExecutorIdentity() {
        return new ExecutorIdentity();
    }

    @Bean
    @ConditionalOnMissingBean
    public DagScanner cronflowDagScanner(ApplicationContext applicationContext,
            CronflowClientProperties properties, Environment environment) {
        String application = properties.getApplication() != null && !properties.getApplication().isBlank()
                ? properties.getApplication()
                : environment.getProperty("spring.application.name", "cronflow-executor");
        return new DagScanner(applicationContext, application);
    }

    @Bean
    @ConditionalOnMissingBean
    public NodeExecutionService cronflowNodeExecutionService(ApplicationContext applicationContext) {
        return new DefaultNodeExecutionService(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public NodeRunController cronflowNodeRunController(NodeExecutionService nodeExecutionService) {
        return new NodeRunController(nodeExecutionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public CronflowClientRegistrar cronflowClientRegistrar(CronflowClientProperties properties,
            Environment environment, DagScanner dagScanner, CronflowServerClient serverClient,
            ExecutorIdentity identity) {
        return new CronflowClientRegistrar(properties, environment, dagScanner, serverClient, identity);
    }

}
