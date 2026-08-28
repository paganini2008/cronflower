package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.chaconneai.spreader.GossipCluster;
import com.github.cronsmith.springapp.scheduler.InMemoryTaskManager;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.jooq.JooqTaskManager;
import com.github.cronsmith.springapp.scheduler.jpa.JpaTaskManager;
import com.github.cronsmith.springapp.scheduler.web.ExecutionController;
import com.github.cronsmith.springapp.scheduler.web.ExecutorController;
import com.github.cronsmith.springapp.scheduler.web.TaskController;

/**
 * Verifies storage selection by the detected {@link StoreType} (no DataSource -> in-memory; a
 * DataSource -> JPA when an EntityManagerFactory is present, else JOOQ), that the full set of server
 * beans is wired, and that the scheduler lifecycle is leader-only unless group sharding is asked for
 * over a shared store. Every scenario uses an in-memory H2 data source or none — never a networked one.
 *
 * @Description: CronsmithServerAutoConfigurationTests
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
class CronsmithServerAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CronsmithServerAutoConfiguration.class))
            .withBean(GossipCluster.class, () -> mock(GossipCluster.class));

    // Adds the application's standard JPA, the wiring a released server.jar uses over a database.
    private final ApplicationContextRunner dbRunner = runner
            .withConfiguration(AutoConfigurations.of(HibernateJpaAutoConfiguration.class))
            .withPropertyValues("spring.jpa.hibernate.ddl-auto=update");

    private static DataSource h2(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    void inMemoryWhenThereIsNoDataSource() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean("cronsmithStorageTaskManager"))
                    .isInstanceOf(InMemoryTaskManager.class);
            assertThat(ctx).hasSingleBean(ClusterTaskManager.class);
            assertThat(ctx.getBean(StoreType.class).isInMemory()).isTrue();
        });
    }

    @Test
    void jpaWhenADataSourceAndEntityManagerFactoryArePresent() {
        dbRunner.withBean(DataSource.class, () -> h2("autocfg_jpa")).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(ClusterTaskManager.class);
            assertThat(ctx.getBean(TaskManager.class)).isInstanceOf(ClusterTaskManager.class);
            assertThat(ctx.getBean("cronsmithStorageTaskManager"))
                    .isInstanceOf(JpaTaskManager.class);
            // The store is auto-detected as node-local H2, so the whole surface is leader-only.
            assertThat(ctx.getBean(StoreType.class)).isEqualTo(StoreType.H2);
            assertThat(ctx).hasSingleBean(ExecutorRegistry.class);
            assertThat(ctx).hasSingleBean(ClusterExecutorRegistry.class);
            assertThat(ctx).hasSingleBean(DefaultTaskDispatcher.class);
            assertThat(ctx).hasSingleBean(SchedulerLifecycle.class);
            assertThat(ctx.getBean(SchedulerLifecycle.class))
                    .isInstanceOf(LeaderSchedulerLifecycle.class);
            assertThat(ctx).hasSingleBean(CronsmithServerBootstrap.class);
            assertThat(ctx).hasSingleBean(ExecutorController.class);
            assertThat(ctx).hasSingleBean(ExecutionController.class);
            assertThat(ctx).hasSingleBean(TaskController.class);
        });
    }

    @Test
    void jooqWhenADataSourceIsPresentButNoJpa() {
        // A DataSource, jOOQ on the classpath, but no EntityManagerFactory: JOOQ is used.
        runner.withBean(DataSource.class, () -> h2("autocfg_jooq")).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean("cronsmithStorageTaskManager"))
                    .isInstanceOf(JooqTaskManager.class);
        });
    }

    @Test
    void shardedLifecycleWhenShardingOverASharedStore() {
        // Force a shared store (as MySQL would detect) so sharding is eligible.
        dbRunner.withBean(StoreType.class, () -> StoreType.MYSQL)
                .withPropertyValues("cronsmith.server.scheduler.sharding=true")
                .withBean(DataSource.class, () -> h2("autocfg_shard")).run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(SchedulerLifecycle.class))
                            .isInstanceOf(ShardedSchedulerLifecycle.class);
                });
    }

    @Test
    void staysLeaderOnlyWhenShardingOnANodeLocalStore() {
        // sharding asked for, but H2 auto-detects as node-local: fall back to leader-only (warns).
        dbRunner.withPropertyValues("cronsmith.server.scheduler.sharding=true")
                .withBean(DataSource.class, () -> h2("autocfg_noshare")).run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(SchedulerLifecycle.class))
                            .isInstanceOf(LeaderSchedulerLifecycle.class);
                });
    }

    @Test
    void serverCanBeDisabled() {
        runner.withPropertyValues("cronsmith.server.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(ClusterTaskManager.class);
        });
    }
}
