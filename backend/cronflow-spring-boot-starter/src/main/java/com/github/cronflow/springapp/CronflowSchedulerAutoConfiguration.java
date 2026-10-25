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
package com.github.cronflow.springapp;

import com.github.cronflow.springapp.scheduler.*;
import com.github.cronflow.springapp.dag.*;
import com.github.cronflow.springapp.web.*;
import com.github.cronflow.springapp.jpa.*;
import com.github.cronflow.springapp.jooq.*;
import com.github.cronflow.springapp.pojo.*;
import java.util.Set;

import java.time.ZoneId;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.openspreader.serialization.ObjectCodecs;
import com.chaconneai.spreader.GossipCluster;
import com.github.cronflow.springapp.scheduler.InMemoryTaskManager;
import com.github.cronflow.springapp.scheduler.TaskManager;
import com.github.cronflow.springapp.jooq.JooqTaskManager;
import com.github.cronflow.springapp.jpa.JpaTaskManager;
import com.github.cronflow.springapp.web.ClusterController;
import com.github.cronflow.springapp.web.CronController;
import com.github.cronflow.springapp.web.ExecutionController;
import com.github.cronflow.springapp.web.ExecutorController;
import com.github.cronflow.springapp.web.ServerExceptionHandler;
import com.github.cronflow.springapp.web.StatsController;
import com.github.cronflow.springapp.web.TaskController;
import jakarta.persistence.EntityManagerFactory;

/**
 * Wires the server (task trigger) side. Active in a cluster ({@link GossipCluster} present) and
 * switchable off with {@code cronflow.scheduler.enabled=false}.
 *
 * <p>
 * The store is not configured by hand: no DataSource means {@link StoreType#IN_MEMORY}; otherwise the
 * {@link StoreType} is auto-detected from the JDBC connection (see {@link StoreType#detect}), so a
 * node-local store is never mistaken for a shared one. A DB store persists through JPA (the
 * application's standard {@code EntityManagerFactory}) or JOOQ (when jOOQ and a {@code DataSource} are
 * present), failing fast if neither is available; the cs_* entities are added to the standard
 * persistence unit and DDL follows the usual {@code spring.jpa.hibernate.ddl-auto}. Whatever is chosen
 * is wrapped by {@link ClusterTaskManager} (a {@link MultipleStoreTaskManager}) so reads stay local and
 * writes route to the leader.
 *
 * @Description: CronflowSchedulerAutoConfiguration
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@AutoConfiguration(afterName = {"com.chaconneai.openspreader.ApplicationClusterAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"})
@ConditionalOnClass(GossipCluster.class)
@ConditionalOnBean(GossipCluster.class)
@ConditionalOnProperty(prefix = "cronflow.scheduler", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(CronflowSchedulerProperties.class)
public class CronflowSchedulerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CronflowSchedulerAutoConfiguration.class);

    /**
     * The store backing the server: {@link StoreType#IN_MEMORY} when there is no DataSource, else
     * auto-detected from the JDBC connection's product name. Its {@code replicated}/{@code shared}
     * nature is intrinsic, so it cannot be misconfigured.
     */
    @Bean
    @ConditionalOnMissingBean
    public StoreType cronflowStoreType(ObjectProvider<DataSource> ds) {
        DataSource dataSource = ds.getIfAvailable();
        StoreType storeType =
                dataSource != null ? StoreType.detect(dataSource) : StoreType.IN_MEMORY;
        log.info("scheduler store: {} (kind={}, replicated={}, shared={}) {}", storeType.name(),
                storeType.metadata("kind"), storeType.isReplicated(), storeType.isShared(),
                storeType.metadata());
        return storeType;
    }

    /**
     * The storage backend. In-memory when the store is {@link StoreType#IN_MEMORY}; otherwise JPA when
     * an {@code EntityManagerFactory} is available, else JOOQ — the classpath decides, not
     * configuration. A DB store with neither available fails fast rather than falling back to memory.
     */
    @Bean(name = "cronflowStorageTaskManager")
    @ConditionalOnMissingBean(name = "cronflowStorageTaskManager")
    public TaskManager cronflowStorageTaskManager(ObjectProvider<EntityManagerFactory> emf,
            ObjectProvider<PlatformTransactionManager> tm, ObjectProvider<DataSource> ds,
            StoreType storeType) {
        if (storeType.isInMemory()) {
            log.info("scheduler storage: in-memory");
            return new InMemoryTaskManager();
        }
        // A DB store: pick JPA vs JOOQ by what is on the classpath. JPA uses the application's standard
        // EntityManagerFactory and transaction manager — the cs_* entities are added to that unit by
        // CronflowSchedulerEntityScanConfiguration; DDL follows the standard spring.jpa.hibernate.ddl-auto.
        EntityManagerFactory entityManagerFactory = emf.getIfAvailable();
        PlatformTransactionManager transactionManager = tm.getIfAvailable();
        if (entityManagerFactory != null && transactionManager != null) {
            log.info("scheduler storage: JPA ({})", storeType.name());
            return new JpaTaskManager(entityManagerFactory, transactionManager);
        }
        DataSource dataSource = ds.getIfAvailable();
        boolean jooqPresent =
                ClassUtils.isPresent("org.jooq.DSLContext", getClass().getClassLoader());
        if (dataSource != null && jooqPresent) {
            log.info("scheduler storage: JOOQ ({})", storeType.name());
            return new JooqTaskManager(dataSource);
        }
        throw new IllegalStateException("scheduler store is " + storeType.name()
                + " but no database access layer is available: add spring-boot-starter-data-jpa (JPA),"
                + " or org.jooq:jooq with a DataSource (JOOQ)");
    }

    @Bean
    @Primary
    public ClusterTaskManager cronflowTaskManager(
            @Qualifier("cronflowStorageTaskManager") TaskManager storage, GossipCluster cluster,
            StoreType storeType, CronflowSchedulerProperties properties) {
        ObjectCodec codec = ObjectCodecs.create(properties.getStorage().getSerialization());
        return new ClusterTaskManager(storage, cluster, codec, storeType, storeType.isReplicated(),
                properties.getStorage().getRequestTimeoutMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutorRegistry cronflowExecutorRegistry(CronflowSchedulerProperties properties) {
        return new ExecutorRegistry(properties.getDispatch().getExecutorTtlMillis(),
                properties.getDispatch().getRouting());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClusterExecutorRegistry cronflowClusterExecutorRegistry(ExecutorRegistry registry,
            GossipCluster cluster, CronflowSchedulerProperties properties) {
        return new ClusterExecutorRegistry(registry, cluster,
                ObjectCodecs.create(properties.getStorage().getSerialization()));
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultTaskDispatcher cronflowTaskDispatcher(ExecutorRegistry registry,
            GossipCluster cluster, CronflowSchedulerProperties properties) {
        return new DefaultTaskDispatcher(registry, cluster,
                ObjectCodecs.create(properties.getStorage().getSerialization()),
                properties.getDispatch());
    }

    /**
     * The scheduler lifecycle. Group sharding — every node triggering its consistent-hash share — is
     * used only when it is asked for ({@code scheduler.sharding=true}) <b>and</b> the detected store is
     * shared ({@link StoreType#isShared()}), whose cluster-wide compare-and-set keeps a task from firing
     * twice during re-sharding. Otherwise, and whenever sharding is asked for on a node-local store,
     * only the leader triggers.
     */
    @Bean
    @ConditionalOnMissingBean
    public SchedulerLifecycle cronflowSchedulerLifecycle(ClusterTaskManager taskManager,
            GossipCluster cluster, StoreType storeType, CronflowSchedulerProperties properties) {
        long window = properties.getWindowMinutes() * 60_000L;
        long interval = properties.getClaimIntervalSeconds() * 1000L;
        ZoneId zone = ZoneId.of(properties.getZone());
        boolean sharding = properties.isSharding();
        if (sharding && storeType.isShared()) {
            log.info("scheduler: group sharding enabled; every node triggers its share");
            GroupShardingStrategy strategy = new GroupShardingStrategy(cluster);
            TaskManager sharded = new ShardingTaskManager(taskManager, strategy);
            return new ShardedSchedulerLifecycle(sharded, cluster, zone, window, interval);
        }
        if (sharding) {
            log.warn("cronflow.scheduler.sharding=true but the store {} is node-local; "
                    + "sharding needs a shared store's cluster-wide CAS, staying leader-only",
                    storeType.name());
        }
        return new LeaderSchedulerLifecycle(taskManager, cluster, zone, window, interval);
    }

    @Bean
    public CronflowSchedulerBootstrap cronflowServerBootstrap(ClusterTaskManager taskManager,
            ClusterExecutorRegistry clusterExecutorRegistry, ExecutorRegistry registry,
            DefaultTaskDispatcher dispatcher, SchedulerLifecycle schedulerLifecycle,
            GossipCluster cluster, CronflowSchedulerProperties properties) {
        return new CronflowSchedulerBootstrap(taskManager, clusterExecutorRegistry, registry,
                dispatcher, schedulerLifecycle, cluster, properties.getDispatch());
    }

    @Bean
    public ExecutorController cronflowExecutorController(TaskManager taskManager,
            ClusterExecutorRegistry executors, ExecutorRegistry registry,
            CronflowSchedulerProperties properties) {
        return new ExecutorController(taskManager, executors, registry,
                properties.getDispatch().getExecutorTtlMillis(),
                ZoneId.of(properties.getZone()));
    }

    @Bean
    public ExecutionController cronflowExecutionController(TaskDispatcher dispatcher) {
        return new ExecutionController(dispatcher);
    }

    @Bean
    public TaskController cronflowTaskController(TaskManager taskManager,
            CronflowSchedulerProperties properties) {
        return new TaskController(taskManager, ZoneId.of(properties.getZone()));
    }

    @Bean
    public ClusterController cronflowClusterController(GossipCluster cluster, StoreType storeType,
            CronflowSchedulerProperties properties) {
        boolean sharding = properties.isSharding() && storeType.isShared();
        return new ClusterController(cluster, storeType, sharding);
    }

    @Bean
    public StatsController cronflowStatsController(TaskManager taskManager,
            ExecutorRegistry registry, CronflowSchedulerProperties properties) {
        return new StatsController(taskManager, registry,
                properties.getDispatch().getExecutorTtlMillis());
    }

    @Bean
    public CronController cronflowCronController(CronflowSchedulerProperties properties) {
        return new CronController(ZoneId.of(properties.getZone()));
    }

    @Bean
    public ServerExceptionHandler cronflowServerExceptionHandler() {
        return new ServerExceptionHandler();
    }

    /**
     * The scheduler REST API namespace. Every controller in {@code ...scheduler.web} is mapped without
     * a prefix; this re-applies {@code cronflow.scheduler.api-prefix} (default {@code /cronflow/scheduler}) in one
     * place via {@link PathMatchConfigurer#addPathPrefix}, so the whole business API lives under
     * {@code <prefix>/**}. Because the prefix is scoped by handler type (this package only), it never
     * touches Actuator ({@code /actuator/**}, served by a different handler mapping) or the host
     * application's own controllers — unlike {@code server.servlet.context-path}, which is
     * container-wide and would drag Actuator under the prefix too. If you change the value, the console
     * proxy and the executor's {@code cronflow.executor.server-api-prefix} must match it.
     */
    @Bean
    public WebMvcConfigurer cronflowSchedulerApiPrefixConfigurer(CronflowSchedulerProperties properties) {
        final String prefix = normalizeApiPrefix(properties.getApiPrefix());
        log.info("cronflow scheduler REST API prefix: {}", prefix.isEmpty() ? "(none, served at root)" : prefix);
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                if (!prefix.isEmpty()) {
                    // Scheduler and DAG controllers now share one package, so scope by the scheduler
                    // controller classes (not the package) to avoid also prefixing the DAG controllers.
                    configurer.addPathPrefix(prefix, SCHEDULER_CONTROLLERS::contains);
                }
            }
        };
    }

    /** The scheduler (task) REST controllers, scoped for the API-prefix path matcher. */
    private static final Set<Class<?>> SCHEDULER_CONTROLLERS = Set.of(TaskController.class,
            ClusterController.class, CronController.class, ExecutionController.class,
            ExecutorController.class, StatsController.class);

    /** Normalize a configured prefix: leading slash, no trailing slash; blank or "/" means none. */
    private static String normalizeApiPrefix(String raw) {
        if (raw == null) {
            return CronflowConstants.SCHEDULER_API_PREFIX;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.equals("/")) {
            return "";
        }
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * When a DataSource is present (a DB store), adds the scheduler cs_* entities to the application's
     * standard persistence unit. The scheduler server runs as its own application with no other
     * business tables, so the standard {@code EntityManagerFactory}, dialect detection and
     * {@code spring.jpa.hibernate.ddl-auto} apply as usual — nothing bespoke. If a user does add their
     * own entities they manage {@code @EntityScan} themselves.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.persistence.EntityManagerFactory")
    @ConditionalOnBean(DataSource.class)
    @EntityScan("com.github.cronflow.springapp.jpa")
    static class CronflowSchedulerEntityScanConfiguration {
    }

}
