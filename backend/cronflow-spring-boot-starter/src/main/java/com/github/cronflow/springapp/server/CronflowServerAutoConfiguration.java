package com.github.cronflow.springapp.server;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.chaconneai.openspreader.dag.StateGraph;
import com.chaconneai.openspreader.serialization.ObjectCodecs;
import com.chaconneai.openspreader.serialization.SerializationType;
import com.chaconneai.spreader.GossipCluster;
import tools.jackson.databind.ObjectMapper;
import com.github.cronflow.springapp.server.jpa.JpaDagRunLog;
import com.github.cronflow.springapp.server.jpa.JpaDagStore;
import com.github.cronflow.springapp.server.jpa.TaskDagEntity;
import com.github.cronflow.springapp.server.jooq.JooqDagRunLog;
import com.github.cronflow.springapp.server.jooq.JooqDagStore;
import com.github.cronflow.springapp.server.web.DagRegistrationController;
import com.github.cronsmith.springapp.scheduler.SchedulerLifecycle;
import com.github.cronsmith.springapp.scheduler.TaskListener;

/**
 * Wires the cronflow server side onto an existing cronsmith scheduler: DAG storage, the executor
 * registry, the HTTP node dispatcher, the coordinator, and the zero-intrusion task-completion seam.
 *
 * <p>
 * Fully pluggable and additive: it loads only when a cronsmith cluster ({@link GossipCluster}) and the
 * DAG kernel ({@link StateGraph}) are present and {@code cronflow.server.enabled} is not {@code false}.
 * A cronsmith-only server never sees it — no {@code cf_task_dag}, no endpoints, no listener. cronsmith
 * is never modified; the listener is attached through its public API.
 *
 * @Description: CronflowServerAutoConfiguration
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@AutoConfiguration(
        afterName = {"com.github.cronsmith.springapp.scheduler.CronsmithServerAutoConfiguration",
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"})
@ConditionalOnClass({GossipCluster.class, StateGraph.class})
@ConditionalOnBean(GossipCluster.class)
@ConditionalOnProperty(prefix = "cronflow.server", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(CronflowServerProperties.class)
public class CronflowServerAutoConfiguration {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(CronflowServerAutoConfiguration.class);

    /**
     * Store backing {@code cf_task_dag}, selected exactly like cronsmith's {@code TaskManager}: JPA when
     * the application's {@code EntityManagerFactory} is present (the default), else jOOQ when jOOQ and a
     * {@code DataSource} are. There is no in-memory variant — a cronflow server persists its definitions;
     * with neither backend it fails fast rather than silently dropping them.
     */
    @Bean
    @ConditionalOnMissingBean
    public DagStore cronflowDagStore(ObjectProvider<DataSource> ds,
            ObjectProvider<EntityManagerFactory> emf) {
        if (emf.getIfAvailable() != null) {
            log.info("cronflow: cf_task_dag store = JPA");
            return new JpaDagStore();
        }
        DataSource dataSource = ds.getIfAvailable();
        if (dataSource != null && jooqPresent()) {
            return new JooqDagStore(dataSource);
        }
        throw new IllegalStateException(noStoreMessage());
    }

    /**
     * DAG run history ({@code cf_dag_log} + {@code cf_dag_node_log}), selected the same way as the
     * definition {@link DagStore}: JPA by default, jOOQ in a jOOQ-only deployment.
     */
    @Bean
    @ConditionalOnMissingBean
    public DagRunLog cronflowDagRunLog(ObjectProvider<DataSource> ds,
            ObjectProvider<EntityManagerFactory> emf) {
        if (emf.getIfAvailable() != null) {
            log.info("cronflow: cf_dag_log / cf_dag_node_log store = JPA");
            return new JpaDagRunLog();
        }
        DataSource dataSource = ds.getIfAvailable();
        if (dataSource != null && jooqPresent()) {
            return new JooqDagRunLog(dataSource);
        }
        throw new IllegalStateException(noStoreMessage());
    }

    private boolean jooqPresent() {
        return ClassUtils.isPresent("org.jooq.DSLContext", getClass().getClassLoader());
    }

    private static String noStoreMessage() {
        return "cronflow server needs a persistent store: add JPA (an EntityManagerFactory)"
                + " or org.jooq:jooq with a DataSource (jOOQ)";
    }

    @Bean
    @ConditionalOnMissingBean
    public DagDefinitionCodec cronflowDagDefinitionCodec(ObjectMapper objectMapper) {
        return new DagDefinitionCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DagExecutorRegistry cronflowDagExecutorRegistry(DagStore dagStore,
            DagDefinitionCodec codec, CronflowServerProperties properties) {
        return new DagExecutorRegistry(dagStore, codec, properties.getDefinitionFormat(),
                properties.getExecutorTtlSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public DagNodeDispatcher cronflowDagNodeDispatcher(DagExecutorRegistry registry) {
        return new DagNodeDispatcher(registry, RestClient.create());
    }

    @Bean
    @ConditionalOnMissingBean
    public DagCoordinator cronflowDagCoordinator(DagExecutorRegistry registry,
            DagNodeDispatcher dispatcher, DagRunLog runLog, ObjectMapper objectMapper) {
        return new DefaultDagCoordinator(registry, dispatcher, runLog, objectMapper);
    }

    /** Cluster-wide sync of the DAG registry over gossip — same pattern as cronsmith's executor list. */
    @Bean
    @ConditionalOnMissingBean
    public ClusterDagRegistry cronflowClusterDagRegistry(DagExecutorRegistry registry,
            GossipCluster cluster) {
        return new ClusterDagRegistry(registry, cluster, ObjectCodecs.create(SerializationType.JDK));
    }

    @Bean
    @ConditionalOnMissingBean
    public DagRegistrationController cronflowDagRegistrationController(ClusterDagRegistry registry) {
        return new DagRegistrationController(registry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "cronflowDagTriggerTaskListener")
    public TaskListener cronflowDagTriggerTaskListener(DagExecutorRegistry registry,
            DagCoordinator coordinator) {
        return new DagTriggerTaskListener(registry, coordinator);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskListenerAttacher cronflowTaskListenerAttacher(
            ObjectProvider<SchedulerLifecycle> lifecycleProvider,
            TaskListener cronflowDagTriggerTaskListener) {
        return new TaskListenerAttacher(lifecycleProvider, cronflowDagTriggerTaskListener);
    }

    /** Applies the cronflow API prefix to the cronflow controllers only (never cronsmith's paths). */
    @Bean
    public WebMvcConfigurer cronflowApiPrefixConfigurer(CronflowServerProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                String prefix = properties.getApiPrefix();
                if (prefix != null && !prefix.isBlank() && !prefix.equals("/")) {
                    configurer.addPathPrefix(prefix,
                            c -> c.getPackageName().startsWith("com.github.cronflow.springapp.server.web"));
                }
            }
        };
    }

    /** Additively registers cf_task_dag alongside cronsmith's entities (does not replace them). */
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = TaskDagEntity.class)
    static class CronflowEntityScanConfiguration {
    }

}
