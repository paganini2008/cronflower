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
package com.github.cronflow.springapp.server;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import java.util.List;
import com.chaconneai.openspreader.dag.Reducer;
import com.chaconneai.openspreader.dag.ProcessingDag;
import com.chaconneai.openspreader.aggregation.ProcessingMapReduce;
import com.github.cronsmith.springapp.scheduler.StoreType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import com.github.cronflow.springapp.server.web.DagQueryController;
import com.github.cronflow.springapp.server.web.DagTriggerController;
import com.github.cronflow.springapp.server.web.DagAuthoringController;
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
    @ConditionalOnMissingBean(name = "cronflowDagRunLogStore")
    public DagRunLog cronflowDagRunLogStore(ObjectProvider<DataSource> ds,
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

    /**
     * The run-log the coordinator writes through: the storage impl wrapped so its writes replicate over
     * gossip in the node-local store model (per-node H2/SQLite), so every node holds the full history —
     * as {@link ClusterDagRegistry} replicates definitions. For a shared store (MySQL/PostgreSQL/…)
     * replication is off: every node already reads the same rows.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "cronflowDagRunLog")
    public ClusterDagRunLog cronflowDagRunLog(
            @Qualifier("cronflowDagRunLogStore") DagRunLog store, GossipCluster cluster,
            ObjectProvider<StoreType> storeType) {
        StoreType st = storeType.getIfAvailable();
        boolean replicate = st == null || !st.isShared();
        return new ClusterDagRunLog(store, cluster, ObjectCodecs.create(SerializationType.JDK),
                replicate);
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

    /** The one node bean the openspreader engine dispatches for every cronflow node (HTTP → executor). */
    @Bean(name = "cronflowNode")
    @ConditionalOnMissingBean(name = "cronflowNode")
    public CronflowNode cronflowNode(DagNodeDispatcher dispatcher) {
        return new CronflowNode(dispatcher);
    }

    /** The node bean for a {@code @DagNode(subgraph=...)} — runs the nested graph on the engine. Depends
     *  on the coordinator lazily (it also is the {@link SubGraphResolver}) to avoid a bean cycle. */
    @Bean(name = "cronflowSubGraph")
    @ConditionalOnMissingBean(name = "cronflowSubGraph")
    public CronflowSubGraphNode cronflowSubGraphNode(ObjectProvider<SubGraphResolver> resolver) {
        return new CronflowSubGraphNode(resolver);
    }

    /** The MapReduce job every sharded node runs on: each shard dispatched to the executor over HTTP.
     *  Present only when the openspreader aggregation toolkit is on (a {@code ProcessingMapReduce}). */
    @Bean(name = "cronflowShardJob")
    @ConditionalOnBean(ProcessingMapReduce.class)
    @ConditionalOnMissingBean(name = "cronflowShardJob")
    public CronflowShardJob cronflowShardJob(DagNodeDispatcher dispatcher) {
        return new CronflowShardJob(dispatcher);
    }

    /** The node bean for a {@code @DagNode(shard=...)} — dynamic fan-out over MapReduce. */
    @Bean(name = "cronflowShardedNode")
    @ConditionalOnBean(ProcessingMapReduce.class)
    @ConditionalOnMissingBean(name = "cronflowShardedNode")
    public CronflowShardedNode cronflowShardedNode(
            ProcessingMapReduce mapReduce) {
        return new CronflowShardedNode(mapReduce);
    }

    /** Runs cronflow DAGs on the openspreader multi-processing engine (distributed across schedulers).
     *  Concrete return type so it is also injectable as {@link SubGraphResolver} (for subgraph nodes). */
    @Bean
    @ConditionalOnMissingBean(DagCoordinator.class)
    public EngineDagRunner cronflowDagCoordinator(
            ProcessingDag dagger, DagExecutorRegistry registry,
            DagRunLog runLog, ObjectMapper objectMapper,
            ObjectProvider<Reducer<?>> reducers) {
        // Custom channel reducers declared as Spring beans join the kernel + cronflow built-ins.
        List<Reducer<?>> custom =
                reducers.stream().toList();
        return new EngineDagRunner(dagger, registry, runLog, objectMapper, custom);
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
    @ConditionalOnMissingBean
    public DagQueryController cronflowDagQueryController(DagExecutorRegistry registry,
            DagRunLog runLog, ObjectProvider<EngineDagRunner> coordinator) {
        return new DagQueryController(registry, runLog, coordinator);
    }

    // DagTriggerController is a @RestController picked up by component scanning (its constructor takes
    // only beans), so no explicit @Bean is needed here.

    /** Canvas authoring endpoints: list target applications, create a graph drawn in the console. */
    @Bean
    @ConditionalOnMissingBean
    public DagAuthoringController cronflowDagAuthoringController(ClusterDagRegistry clusterRegistry,
            DagExecutorRegistry registry) {
        return new DagAuthoringController(clusterRegistry, registry);
    }

    /** Contributes a {@code cronflow} component to {@code /actuator/health} so the console can detect
     *  the DAG feature (present only when cronflow is deployed) and show or hide its menu accordingly. */
    @Bean
    @ConditionalOnClass(org.springframework.boot.health.contributor.HealthIndicator.class)
    @ConditionalOnMissingBean(name = "cronflowHealthIndicator")
    public CronflowHealthIndicator cronflowHealthIndicator(DagExecutorRegistry registry) {
        return new CronflowHealthIndicator(registry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "cronflowDagTriggerTaskListener")
    public TaskListener cronflowDagTriggerTaskListener(DagExecutorRegistry registry,
            DagCoordinator coordinator, ObjectMapper objectMapper) {
        return new DagTriggerTaskListener(registry, coordinator, objectMapper);
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

    /** Additively registers cf_* entities alongside cronsmith's (does not replace them). Guarded by the
     *  JPA API's presence — as cronsmith guards its own entity scan — so a jOOQ deployment without a
     *  persistence unit never tries to load the entities. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.persistence.EntityManagerFactory")
    @EntityScan(basePackageClasses = TaskDagEntity.class)
    static class CronflowEntityScanConfiguration {
    }

}
