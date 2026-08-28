package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.mockito.Mockito.mock;
import com.chaconneai.spreader.GossipCluster;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskStatus;

/**
 * Exercises the JPA storage backend end to end against an in-memory H2 database (never a networked
 * one): save, read, status transitions, compare-and-set, execution logging with counters, misfire
 * counting, query/paging and removal all go through Hibernate and back.
 *
 * @Description: JpaTaskManagerTests
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
class JpaTaskManagerTests {

    // DB mode over the application's standard JPA (HibernateJpaAutoConfiguration), the same wiring a
    // released server.jar uses — the cs_* entities are added by the starter's @EntityScan and DDL
    // follows the standard spring.jpa.hibernate.ddl-auto.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
                    CronsmithServerAutoConfiguration.class))
            .withPropertyValues("spring.jpa.hibernate.ddl-auto=update")
            .withBean(GossipCluster.class, () -> mock(GossipCluster.class))
            .withBean(DataSource.class, JpaTaskManagerTests::h2);

    private static DataSource h2() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:jpa_tm;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static HttpDispatchCustomTask task(String name) {
        return HttpDispatchCustomTask.fromMetadata(new ExecutorTaskMetadata(TaskId.DEFAULT_GROUP,
                name, "com.demo.Tasks", "demoTasks", "run", "0 0 12 * * ?", "d", "p", 0L, 0, 1000L,
                "FIRE_ONCE_NOW"), "demo-app");
    }

    @Test
    void fullLifecycleThroughHibernate() {
        // BASE anchored after "now" so the daily-noon window is deterministic on any run date.
        LocalDateTime base = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS);
        runner.run(ctx -> {
            TaskManager tm = (TaskManager) ctx.getBean("cronsmithStorageTaskManager");

            // save -> STANDBY, initialParameter kept
            TaskDetail saved = tm.saveTask(task("daily"), "p");
            assertThat(saved.getTaskStatus()).isEqualTo(TaskStatus.STANDBY);
            assertThat(saved.getInitialParameter()).isEqualTo("p");
            assertThat(tm.hasTask(TaskId.of("daily"))).isTrue();

            // schedule survives the round trip and computes noon
            LocalDateTime next = tm.computeNextFiredDateTime(TaskId.of("daily"), base);
            assertThat(next.getHour()).isEqualTo(12);

            // status state machine
            assertThat(tm.setTaskStatus(TaskId.of("daily"), TaskStatus.RUNNING))
                    .as("STANDBY cannot jump straight to RUNNING").isFalse();
            assertThat(tm.setTaskStatus(TaskId.of("daily"), TaskStatus.SCHEDULED)).isTrue();
            assertThat(tm.compareAndSetTaskStatus(TaskId.of("daily"), TaskStatus.SCHEDULED,
                    TaskStatus.RUNNING)).isTrue();
            assertThat(tm.compareAndSetTaskStatus(TaskId.of("daily"), TaskStatus.SCHEDULED,
                    TaskStatus.RUNNING)).as("stale expected value loses the CAS").isFalse();

            // execution logging + counters
            TaskId id = TaskId.of("daily");
            tm.recordExecution(new TaskExecutionLog(id, base).attempt(0).success(false).elapsed(5)
                    .error(new IllegalStateException("boom")));
            tm.recordExecution(new TaskExecutionLog(id, base.plusSeconds(1)).attempt(1).success(true)
                    .elapsed(7).returnValue("ok"));
            List<TaskExecutionLog> logs = tm.findExecutionLogs(id, 10, 0);
            assertThat(logs).hasSize(2);
            assertThat(logs.get(0).isSuccess()).as("newest attempt first").isTrue();
            TaskDetail afterRuns = tm.getTaskDetail(id, true);
            assertThat(afterRuns.getRunCount()).isEqualTo(2L);
            assertThat(afterRuns.getFailureCount()).isEqualTo(1L);

            // misfire counting
            tm.recordMisfire(id, base);
            tm.recordMisfire(id, base.plusMinutes(1));
            assertThat(tm.getTaskDetail(id, true).getMisfireCount()).isEqualTo(2L);

            // query + paging
            for (int i = 0; i < 5; i++) {
                tm.saveTask(task("job-" + i), null);
            }
            assertThat(tm.getTaskCount(TaskQuery.newQuery())).isEqualTo(6); // daily + 5 jobs
            assertThat(tm.findTaskDetails(TaskQuery.newQuery().limit(3))).hasSize(3);
            assertThat(tm.findTaskDetails(TaskQuery.newQuery().name("job-2"))).hasSize(1);

            // removal
            assertThat(tm.removeTask(TaskId.of("job-0"))).isNotNull();
            assertThat(tm.hasTask(TaskId.of("job-0"))).isFalse();
            assertThat(tm.getTaskCount(TaskQuery.newQuery())).isEqualTo(5);
        });
    }
}
