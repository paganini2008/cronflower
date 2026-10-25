package com.github.cronflow.springapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import com.chaconneai.openspreader.aggregation.ProcessingMapReduce;
import com.chaconneai.openspreader.dag.ProcessingDag;
import com.chaconneai.spreader.GossipCluster;
import com.github.cronflow.springapp.dag.ClusterDagRegistry;
import com.github.cronflow.springapp.dag.ClusterDagRunLog;
import com.github.cronflow.springapp.dag.CronflowNode;
import com.github.cronflow.springapp.dag.CronflowShardJob;
import com.github.cronflow.springapp.dag.CronflowShardedNode;
import com.github.cronflow.springapp.dag.CronflowSubGraphNode;
import com.github.cronflow.springapp.dag.DagExecutorRegistry;
import com.github.cronflow.springapp.dag.DagNodeDispatcher;
import com.github.cronflow.springapp.dag.DagRunLog;
import com.github.cronflow.springapp.dag.DagStore;
import com.github.cronflow.springapp.dag.EngineDagRunner;
import com.github.cronflow.springapp.jpa.JpaDagRunLog;
import com.github.cronflow.springapp.jpa.JpaDagStore;
import com.github.cronflow.springapp.pojo.DagNodeView;
import com.github.cronflow.springapp.pojo.DagRunView;
import com.github.cronflow.springapp.pojo.StoredDag;
import com.github.cronflow.springapp.web.CronflowNodeHealthController;
import com.github.cronflow.springapp.web.DagAuthoringController;
import com.github.cronflow.springapp.web.DagQueryController;
import com.github.cronflow.springapp.web.DagRegistrationController;
import com.github.cronflow.springapp.web.DagTriggerController;
import tools.jackson.databind.ObjectMapper;

/**
 * Boots the cronflow server auto-configuration over an in-memory H2 database with the engine beans
 * mocked, asserting the full set of DAG beans is wired, and then exercises the JPA store and run-log
 * end to end through the live context (definitions, run history, node log). Never a networked database.
 */
class CronflowServerAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CronflowSchedulerAutoConfiguration.class,
                    CronflowServerAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                    TransactionAutoConfiguration.class))
            .withPropertyValues("spring.jpa.hibernate.ddl-auto=update")
            .withBean(GossipCluster.class, () -> mock(GossipCluster.class))
            .withBean(ProcessingDag.class, () -> mock(ProcessingDag.class))
            .withBean(ProcessingMapReduce.class, () -> mock(ProcessingMapReduce.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(DataSource.class, CronflowServerAutoConfigurationTests::h2);

    private static DataSource h2() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:cronflow_server;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    void wiresEveryDagBeanOverJpa() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(DagStore.class)).isInstanceOf(JpaDagStore.class);
            assertThat(ctx.getBean("cronflowDagRunLogStore")).isInstanceOf(JpaDagRunLog.class);
            assertThat(ctx.getBean(DagRunLog.class)).isInstanceOf(ClusterDagRunLog.class);
            assertThat(ctx).hasSingleBean(DagExecutorRegistry.class);
            assertThat(ctx).hasSingleBean(DagNodeDispatcher.class);
            assertThat(ctx).hasSingleBean(EngineDagRunner.class);
            assertThat(ctx).hasSingleBean(ClusterDagRegistry.class);
            assertThat(ctx).hasSingleBean(CronflowNode.class);
            assertThat(ctx).hasSingleBean(CronflowSubGraphNode.class);
            assertThat(ctx).hasSingleBean(CronflowShardJob.class);
            assertThat(ctx).hasSingleBean(CronflowShardedNode.class);
            assertThat(ctx).hasSingleBean(CronflowHealthIndicator.class);
            assertThat(ctx).hasSingleBean(DagRegistrationController.class);
            assertThat(ctx).hasSingleBean(DagQueryController.class);
            assertThat(ctx).hasSingleBean(DagTriggerController.class);
            assertThat(ctx).hasSingleBean(DagAuthoringController.class);
            assertThat(ctx).hasSingleBean(CronflowNodeHealthController.class);
            // the health indicator reports the feature and graph count
            assertThat(ctx.getBean(CronflowHealthIndicator.class).health().getDetails())
                    .containsEntry("feature", "cronflow");
        });
    }

    @Test
    void jpaDagStoreRoundTrips() {
        runner.run(ctx -> {
            DagStore store = ctx.getBean(DagStore.class);
            store.save("app", "g1", "{\"graph\":\"g1\"}", "json");
            store.save("app", "g1", "{\"graph\":\"g1\",\"v\":2}", "json"); // update existing
            store.save("app", "g2", "{\"graph\":\"g2\"}", "json");
            List<StoredDag> all = store.loadAll();
            assertThat(all).hasSize(2);
            assertThat(all).extracting(StoredDag::graph).containsExactlyInAnyOrder("g1", "g2");
        });
    }

    @Test
    void jpaDagRunLogRoundTrips() {
        runner.run(ctx -> {
            DagRunLog log = (DagRunLog) ctx.getBean("cronflowDagRunLogStore");
            LocalDateTime now = LocalDateTime.now();
            log.begin("r1", null, "app", "g", "task", now, "{}");
            log.node("r1", "g", "n1", 0, "SUCCESS", "{}", "{\"o\":1}", "exec", 5L, null);
            log.node("r1", "g", "n2", 1, "SUCCESS", "{}", "{}", "exec", 6L, null);
            log.finish("r1", "SUCCESS", now.plusSeconds(1), 1000L, 2, null, null, "{\"done\":true}");
            // a child (subgraph) run
            log.begin("r2", "r1", "app", "child", "subgraph", now, "{}");
            log.finish("r2", "SUCCESS", now.plusSeconds(1), 10L, 1, null, null, "{}");

            DagRunView run = log.findRun("r1");
            assertThat(run).isNotNull();
            assertThat(run.status()).isEqualTo("SUCCESS");
            assertThat(run.nodeCount()).isEqualTo(2);
            assertThat(log.findRun("nope")).isNull();

            List<DagNodeView> nodes = log.nodesOf("r1");
            assertThat(nodes).hasSize(2);
            assertThat(nodes.get(0).node()).isEqualTo("n1");

            assertThat(log.listRuns(null, null, null, 10, 0)).hasSizeGreaterThanOrEqualTo(2);
            assertThat(log.listRuns("app", "g", "SUCCESS", 10, 0)).hasSize(1);
            assertThat(log.countRuns("app", "g", "SUCCESS")).isEqualTo(1);
            assertThat(log.countRuns(null, null, null)).isGreaterThanOrEqualTo(2);
            assertThat(log.childRuns("r1")).hasSize(1);
        });
    }

    @Test
    void serverDisabledSkipsCronflow() {
        runner.withPropertyValues("cronflow.server.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(DagExecutorRegistry.class);
        });
    }

    @Test
    void usesJooqStoreWhenNoEntityManagerFactory() {
        // No HibernateJpaAutoConfiguration -> no EntityManagerFactory -> jOOQ store (jooq is on the
        // test classpath) backed by the H2 DataSource.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CronflowSchedulerAutoConfiguration.class,
                        CronflowServerAutoConfiguration.class))
                .withBean(GossipCluster.class, () -> mock(GossipCluster.class))
                .withBean(ProcessingDag.class, () -> mock(ProcessingDag.class))
                .withBean(ProcessingMapReduce.class, () -> mock(ProcessingMapReduce.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(DataSource.class, CronflowServerAutoConfigurationTests::h2)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(DagStore.class))
                            .isInstanceOf(com.github.cronflow.springapp.jooq.JooqDagStore.class);
                    assertThat(ctx.getBean("cronflowDagRunLogStore"))
                            .isInstanceOf(com.github.cronflow.springapp.jooq.JooqDagRunLog.class);
                });
    }

    @Test
    void failsFastWithoutAnyStore() {
        // No DataSource and no JPA -> the definition store cannot be chosen -> context fails.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CronflowSchedulerAutoConfiguration.class,
                        CronflowServerAutoConfiguration.class))
                .withBean(GossipCluster.class, () -> mock(GossipCluster.class))
                .withBean(ProcessingDag.class, () -> mock(ProcessingDag.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void apiPrefixConfigurerAppliesPrefixToDagControllersOnly() {
        runner.run(ctx -> {
            org.springframework.web.servlet.config.annotation.WebMvcConfigurer configurer =
                    ctx.getBean("cronflowApiPrefixConfigurer",
                            org.springframework.web.servlet.config.annotation.WebMvcConfigurer.class);
            // a real prefix is applied
            configurer.configurePathMatch(
                    new org.springframework.web.servlet.config.annotation.PathMatchConfigurer());
        });
        // a blank prefix is a no-op path
        runner.withPropertyValues("cronflow.server.api-prefix=").run(ctx -> {
            org.springframework.web.servlet.config.annotation.WebMvcConfigurer configurer =
                    ctx.getBean("cronflowApiPrefixConfigurer",
                            org.springframework.web.servlet.config.annotation.WebMvcConfigurer.class);
            configurer.configurePathMatch(
                    new org.springframework.web.servlet.config.annotation.PathMatchConfigurer());
        });
    }
}
