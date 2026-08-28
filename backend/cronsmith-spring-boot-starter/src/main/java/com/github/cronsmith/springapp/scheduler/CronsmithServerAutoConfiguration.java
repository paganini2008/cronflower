package com.github.cronsmith.springapp.scheduler;

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
import com.github.cronsmith.springapp.scheduler.InMemoryTaskManager;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.jooq.JooqTaskManager;
import com.github.cronsmith.springapp.scheduler.jpa.JpaTaskManager;
import com.github.cronsmith.springapp.scheduler.web.ClusterController;
import com.github.cronsmith.springapp.scheduler.web.CronController;
import com.github.cronsmith.springapp.scheduler.web.ExecutionController;
import com.github.cronsmith.springapp.scheduler.web.ExecutorController;
import com.github.cronsmith.springapp.scheduler.web.ServerExceptionHandler;
import com.github.cronsmith.springapp.scheduler.web.StatsController;
import com.github.cronsmith.springapp.scheduler.web.TaskController;
import jakarta.persistence.EntityManagerFactory;

/**
 * Wires the server (task trigger) side. Active in a cluster ({@link GossipCluster} present) and
 * switchable off with {@code cronsmith.server.enabled=false}.
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
 * @Description: CronsmithServerAutoConfiguration
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@AutoConfiguration(afterName = {"com.chaconneai.openspreader.ApplicationClusterAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"})
@ConditionalOnClass(GossipCluster.class)
@ConditionalOnBean(GossipCluster.class)
@ConditionalOnProperty(prefix = "cronsmith.server", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(CronsmithServerProperties.class)
public class CronsmithServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CronsmithServerAutoConfiguration.class);

    /**
     * The store backing the server: {@link StoreType#IN_MEMORY} when there is no DataSource, else
     * auto-detected from the JDBC connection's product name. Its {@code replicated}/{@code shared}
     * nature is intrinsic, so it cannot be misconfigured.
     */
    @Bean
    @ConditionalOnMissingBean
    public StoreType cronsmithStoreType(ObjectProvider<DataSource> ds) {
        DataSource dataSource = ds.getIfAvailable();
        StoreType storeType =
                dataSource != null ? StoreType.detect(dataSource) : StoreType.IN_MEMORY;
        log.info("cronsmith store: {} (kind={}, replicated={}, shared={}) {}", storeType.name(),
                storeType.metadata("kind"), storeType.isReplicated(), storeType.isShared(),
                storeType.metadata());
        return storeType;
    }

    /**
     * The storage backend. In-memory when the store is {@link StoreType#IN_MEMORY}; otherwise JPA when
     * a cronsmith {@code EntityManagerFactory} is available, else JOOQ — the classpath decides, not
     * configuration. A DB store with neither available fails fast rather than falling back to memory.
     */
    @Bean(name = "cronsmithStorageTaskManager")
    @ConditionalOnMissingBean(name = "cronsmithStorageTaskManager")
    public TaskManager cronsmithStorageTaskManager(ObjectProvider<EntityManagerFactory> emf,
            ObjectProvider<PlatformTransactionManager> tm, ObjectProvider<DataSource> ds,
            StoreType storeType) {
        if (storeType.isInMemory()) {
            log.info("cronsmith storage: in-memory");
            return new InMemoryTaskManager();
        }
        // A DB store: pick JPA vs JOOQ by what is on the classpath. JPA uses the application's standard
        // EntityManagerFactory and transaction manager — the cs_* entities are added to that unit by
        // CronsmithEntityScanConfiguration; DDL follows the standard spring.jpa.hibernate.ddl-auto.
        EntityManagerFactory entityManagerFactory = emf.getIfAvailable();
        PlatformTransactionManager transactionManager = tm.getIfAvailable();
        if (entityManagerFactory != null && transactionManager != null) {
            log.info("cronsmith storage: JPA ({})", storeType.name());
            return new JpaTaskManager(entityManagerFactory, transactionManager);
        }
        DataSource dataSource = ds.getIfAvailable();
        boolean jooqPresent =
                ClassUtils.isPresent("org.jooq.DSLContext", getClass().getClassLoader());
        if (dataSource != null && jooqPresent) {
            log.info("cronsmith storage: JOOQ ({})", storeType.name());
            return new JooqTaskManager(dataSource);
        }
        throw new IllegalStateException("cronsmith store is " + storeType.name()
                + " but no database access layer is available: add spring-boot-starter-data-jpa (JPA),"
                + " or org.jooq:jooq with a DataSource (JOOQ)");
    }

    @Bean
    @Primary
    public ClusterTaskManager cronsmithTaskManager(
            @Qualifier("cronsmithStorageTaskManager") TaskManager storage, GossipCluster cluster,
            StoreType storeType, CronsmithServerProperties properties) {
        ObjectCodec codec = ObjectCodecs.create(properties.getStorage().getSerialization());
        return new ClusterTaskManager(storage, cluster, codec, storeType, storeType.isReplicated(),
                properties.getStorage().getRequestTimeoutMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutorRegistry cronsmithExecutorRegistry(CronsmithServerProperties properties) {
        return new ExecutorRegistry(properties.getDispatch().getExecutorTtlMillis(),
                properties.getDispatch().getRouting());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClusterExecutorRegistry cronsmithClusterExecutorRegistry(ExecutorRegistry registry,
            GossipCluster cluster, CronsmithServerProperties properties) {
        return new ClusterExecutorRegistry(registry, cluster,
                ObjectCodecs.create(properties.getStorage().getSerialization()));
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultTaskDispatcher cronsmithTaskDispatcher(ExecutorRegistry registry,
            GossipCluster cluster, CronsmithServerProperties properties) {
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
    public SchedulerLifecycle cronsmithSchedulerLifecycle(ClusterTaskManager taskManager,
            GossipCluster cluster, StoreType storeType, CronsmithServerProperties properties) {
        long window = properties.getScheduler().getWindowMinutes() * 60_000L;
        long interval = properties.getScheduler().getClaimIntervalSeconds() * 1000L;
        ZoneId zone = ZoneId.of(properties.getScheduler().getZone());
        boolean sharding = properties.getScheduler().isSharding();
        if (sharding && storeType.isShared()) {
            log.info("cronsmith scheduler: group sharding enabled; every node triggers its share");
            GroupShardingStrategy strategy = new GroupShardingStrategy(cluster);
            TaskManager sharded = new ShardingTaskManager(taskManager, strategy);
            return new ShardedSchedulerLifecycle(sharded, cluster, zone, window, interval);
        }
        if (sharding) {
            log.warn("cronsmith.server.scheduler.sharding=true but the store {} is node-local; "
                    + "sharding needs a shared store's cluster-wide CAS, staying leader-only",
                    storeType.name());
        }
        return new LeaderSchedulerLifecycle(taskManager, cluster, zone, window, interval);
    }

    @Bean
    public CronsmithServerBootstrap cronsmithServerBootstrap(ClusterTaskManager taskManager,
            ClusterExecutorRegistry clusterExecutorRegistry, ExecutorRegistry registry,
            DefaultTaskDispatcher dispatcher, SchedulerLifecycle schedulerLifecycle,
            GossipCluster cluster, CronsmithServerProperties properties) {
        return new CronsmithServerBootstrap(taskManager, clusterExecutorRegistry, registry,
                dispatcher, schedulerLifecycle, cluster, properties.getDispatch());
    }

    @Bean
    public ExecutorController cronsmithExecutorController(TaskManager taskManager,
            ClusterExecutorRegistry executors, ExecutorRegistry registry,
            CronsmithServerProperties properties) {
        return new ExecutorController(taskManager, executors, registry,
                properties.getDispatch().getExecutorTtlMillis(),
                ZoneId.of(properties.getScheduler().getZone()));
    }

    @Bean
    public ExecutionController cronsmithExecutionController(TaskDispatcher dispatcher) {
        return new ExecutionController(dispatcher);
    }

    @Bean
    public TaskController cronsmithTaskController(TaskManager taskManager,
            CronsmithServerProperties properties) {
        return new TaskController(taskManager, ZoneId.of(properties.getScheduler().getZone()));
    }

    @Bean
    public ClusterController cronsmithClusterController(GossipCluster cluster, StoreType storeType,
            CronsmithServerProperties properties) {
        boolean sharding = properties.getScheduler().isSharding() && storeType.isShared();
        return new ClusterController(cluster, storeType, sharding);
    }

    @Bean
    public StatsController cronsmithStatsController(TaskManager taskManager,
            ExecutorRegistry registry, CronsmithServerProperties properties) {
        return new StatsController(taskManager, registry,
                properties.getDispatch().getExecutorTtlMillis());
    }

    @Bean
    public CronController cronsmithCronController(CronsmithServerProperties properties) {
        return new CronController(ZoneId.of(properties.getScheduler().getZone()));
    }

    @Bean
    public ServerExceptionHandler cronsmithServerExceptionHandler() {
        return new ServerExceptionHandler();
    }

    /**
     * The cronsmith REST API namespace. Every controller in {@code ...scheduler.web} is mapped without
     * a prefix; this re-applies {@code cronsmith.server.api-prefix} (default {@code /cronsmith}) in one
     * place via {@link PathMatchConfigurer#addPathPrefix}, so the whole business API lives under
     * {@code <prefix>/**}. Because the prefix is scoped by handler type (this package only), it never
     * touches Actuator ({@code /actuator/**}, served by a different handler mapping) or the host
     * application's own controllers — unlike {@code server.servlet.context-path}, which is
     * container-wide and would drag Actuator under the prefix too. If you change the value, the console
     * proxy and the executor's {@code cronsmith.client.server-api-prefix} must match it.
     */
    @Bean
    public WebMvcConfigurer cronsmithApiPrefixConfigurer(CronsmithServerProperties properties) {
        final String prefix = normalizeApiPrefix(properties.getApiPrefix());
        log.info("cronsmith REST API prefix: {}", prefix.isEmpty() ? "(none, served at root)" : prefix);
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                if (!prefix.isEmpty()) {
                    configurer.addPathPrefix(prefix, HandlerTypePredicate
                            .forBasePackage("com.github.cronsmith.springapp.scheduler.web"));
                }
            }
        };
    }

    /** Normalize a configured prefix: leading slash, no trailing slash; blank or "/" means none. */
    private static String normalizeApiPrefix(String raw) {
        if (raw == null) {
            return "/cronsmith";
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
     * When a DataSource is present (a DB store), adds the cronsmith cs_* entities to the application's
     * standard persistence unit. The cronsmith server runs as its own application with no other
     * business tables, so the standard {@code EntityManagerFactory}, dialect detection and
     * {@code spring.jpa.hibernate.ddl-auto} apply as usual — nothing bespoke. If a user does add their
     * own entities they manage {@code @EntityScan} themselves.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.persistence.EntityManagerFactory")
    @ConditionalOnBean(DataSource.class)
    @EntityScan("com.github.cronsmith.springapp.scheduler.jpa")
    static class CronsmithEntityScanConfiguration {
    }

}
