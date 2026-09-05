package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.chaconneai.spreader.GossipCluster;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Live regression of the JPA/Hibernate storage backend against a real SQL Server and Oracle.
 *
 * <p>
 * These two are jOOQ-commercial-only dialects, so — unlike H2 / SQLite / PostgreSQL / MySQL, which
 * the jOOQ matrix covers — they can only be exercised through the JPA store. Hibernate owns the DDL
 * here ({@code ddl-auto=update}), so this also proves the entity mapping — including the new
 * {@code repeat_count} / {@code stop_at} columns — is generated correctly on each dialect.
 *
 * <p>
 * A database that is not reachable is skipped, so the suite still passes on a machine without them.
 * Connection details come from system properties, defaulting to the local docker containers
 * (database {@code demo}, user {@code fengy}).
 *
 * @Description: JpaRealDatabaseRegressionTests
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 1.0.0
 */
class JpaRealDatabaseRegressionTests {

    /** One target database: how to reach it and what identifies it in the test name. */
    record TargetDb(String name, String driver, String url, String user, String password) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<TargetDb> databases() {
        String user = System.getProperty("cronsmith.regress.user", "fengy");
        String password = System.getProperty("cronsmith.regress.password", "123456");
        String database = System.getProperty("cronsmith.regress.database", "demo");
        return Stream.of(
                new TargetDb("SQLSERVER", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                        "jdbc:sqlserver://" + host("sqlserver", "localhost") + ":"
                                + System.getProperty("cronsmith.sqlserver.port", "1433")
                                + ";databaseName=" + database
                                + ";encrypt=false;trustServerCertificate=true;loginTimeout=3",
                        user, password),
                new TargetDb("ORACLE", "oracle.jdbc.OracleDriver",
                        "jdbc:oracle:thin:@//" + host("oracle", "localhost") + ":"
                                + System.getProperty("cronsmith.oracle.port", "1521") + "/"
                                + System.getProperty("cronsmith.oracle.service", database),
                        user, password));
    }

    private static String host(String db, String fallback) {
        return System.getProperty("cronsmith." + db + ".host", fallback);
    }

    private ApplicationContextRunner runnerFor(TargetDb db) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
                        CronsmithServerAutoConfiguration.class))
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=update")
                .withBean(GossipCluster.class, () -> mock(GossipCluster.class))
                .withBean(DataSource.class, () -> dataSource(db));
    }

    private static DataSource dataSource(TargetDb db) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(db.driver());
        config.setJdbcUrl(db.url());
        config.setUsername(db.user());
        config.setPassword(db.password());
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(4000L);
        config.setInitializationFailTimeout(-1L);
        return new HikariDataSource(config);
    }

    private static boolean reachable(TargetDb db) {
        DriverManager.setLoginTimeout(3);
        try (Connection connection =
                DriverManager.getConnection(db.url(), db.user(), db.password())) {
            return connection.isValid(3);
        } catch (Throwable e) {
            return false;
        }
    }

    private static HttpDispatchCustomTask task(String name, int repeatCount, LocalDateTime stopAt) {
        return HttpDispatchCustomTask.fromMetadata(new ExecutorTaskMetadata(TaskId.DEFAULT_GROUP,
                name, "com.demo.Tasks", "demoTasks", "run", "0 0 12 * * ?", "cron", "d", "p", 0L, 0,
                1000L, "FIRE_ONCE_NOW", repeatCount, stopAt != null ? stopAt.toString() : null),
                "demo-app");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    void lifecycleWithRepeatAndStopAt(TargetDb db) {
        assumeTrue(reachable(db), db + " is not reachable; skipping.");
        LocalDateTime base = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS);
        LocalDateTime stopAt = LocalDateTime.now().plusYears(1).truncatedTo(ChronoUnit.SECONDS);
        runnerFor(db).run(ctx -> {
            TaskManager tm = (TaskManager) ctx.getBean("cronsmithStorageTaskManager");

            // Clean any leftovers from a prior run so counters and status start fresh.
            tm.removeTask(TaskId.of("capped"));
            tm.removeTask(TaskId.of("deadline"));

            // --- the new columns round-trip through the dialect's DDL/type mapping ---
            TaskDetail saved = tm.saveTask(task("capped", 2, stopAt), "p");
            assertThat(saved.getTaskStatus()).isEqualTo(TaskStatus.STANDBY);
            TaskDetail readBack = tm.getTaskDetail(TaskId.of("capped"), true);
            assertThat(readBack.getTask().getRepeatCount()).isEqualTo(2);
            assertThat(readBack.getTask().getStopAt()).isEqualTo(stopAt);

            // --- repeatCount is enforced: once runCount reaches the cap, there is no next fire ---
            assertThat(tm.computeNextFiredDateTime(TaskId.of("capped"), base))
                    .as("first fire allowed (runCount 0 < 2)").isNotNull();
            tm.recordExecution(new TaskExecutionLog(TaskId.of("capped"), base).attempt(0)
                    .success(true).elapsed(1));
            tm.recordExecution(new TaskExecutionLog(TaskId.of("capped"), base.plusSeconds(1))
                    .attempt(0).success(true).elapsed(1));
            assertThat(tm.getTaskDetail(TaskId.of("capped"), true).getRunCount()).isEqualTo(2L);
            assertThat(tm.computeNextFiredDateTime(TaskId.of("capped"), base))
                    .as("repeat cap reached -> no next occurrence").isNull();

            // --- stopAt is enforced: a deadline in the past leaves no future occurrence ---
            tm.saveTask(task("deadline", -1, LocalDateTime.now().minusDays(1)), "p");
            assertThat(tm.computeNextFiredDateTime(TaskId.of("deadline"), base))
                    .as("next fire is after a past stopAt -> finished").isNull();

            // Data is left in place on purpose so the rows can be inspected after the run; the
            // leading removeTask calls make a rerun idempotent.
        });
    }
}
