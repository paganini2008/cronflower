package com.github.cronsmith.springapp.scheduler.test;

import java.time.LocalDateTime;
import java.time.Month;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.jooq.JooqTaskManager;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.PersistentTask;

/**
 * 
 * Not a test of behaviour: a helper that fills the real PostgreSQL and MySQL databases with a small,
 * readable set of tasks and execution logs and leaves them in place, so the stored shape can be
 * inspected by hand. It is skipped unless a server is reachable and never drops what it wrote.
 * 
 * <p>
 * Run it on demand with {@code mvn test -Dtest=DataInspectionPopulator}.
 * 
 * @Description: DataInspectionPopulator
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class DataInspectionPopulator {

    private static final LocalDateTime BASE =
            LocalDateTime.of(2026, Month.AUGUST, 24, 10, 0, 0);

    @Test
    public void populatePostgresql() throws Exception {
        populate(DatabaseProvider.POSTGRESQL);
    }

    @Test
    public void populateMysql() throws Exception {
        populate(DatabaseProvider.MYSQL);
    }

    private void populate(DatabaseProvider provider) throws Exception {
        org.junit.Assume.assumeTrue(provider + " not reachable", provider.isAvailable());
        DataSource dataSource = provider.createDataSource();
        provider.initializeSchema(dataSource);
        JooqTaskManager manager = new JooqTaskManager(dataSource);

        // A daily report, scheduled with a computed next fire time.
        manager.saveTask(new PersistentTask("reports", "daily-report", "0 0 12 * * ?"), "Q3-2026");
        manager.setTaskStatus(TaskId.of("reports", "daily-report"), TaskStatus.SCHEDULED);
        manager.computeNextFiredDateTime(TaskId.of("reports", "daily-report"), BASE);
        manager.recordExecution(new TaskExecutionLog(TaskId.of("reports", "daily-report"), BASE)
                .firedAt(BASE.plusSeconds(1)).completedAt(BASE.plusSeconds(2)).elapsed(1200)
                .attempt(0).success(true).returnValue("rows=1043"));

        // A flaky sync that failed once then succeeded, so both a failure and a success row exist.
        manager.saveTask(new PersistentTask("etl", "hourly-sync", "0 0 * * * ?"), null);
        manager.setTaskStatus(TaskId.of("etl", "hourly-sync"), TaskStatus.SCHEDULED);
        manager.computeNextFiredDateTime(TaskId.of("etl", "hourly-sync"), BASE);
        manager.recordExecution(new TaskExecutionLog(TaskId.of("etl", "hourly-sync"), BASE)
                .firedAt(BASE.plusSeconds(1)).elapsed(500).attempt(0).success(false)
                .error(new IllegalStateException("connection refused")));
        manager.recordExecution(new TaskExecutionLog(TaskId.of("etl", "hourly-sync"), BASE)
                .firedAt(BASE.plusSeconds(2)).elapsed(800).attempt(1).success(true)
                .returnValue("synced 320 records"));
        manager.recordMisfire(TaskId.of("etl", "hourly-sync"), BASE.minusHours(1));

        // A paused cleanup job and a finished one, to show the range of statuses.
        manager.saveTask(new PersistentTask("maintenance", "cleanup", "*/30 * * * * ?"), null);
        manager.setTaskStatus(TaskId.of("maintenance", "cleanup"), TaskStatus.PAUSED);

        manager.saveTask(new PersistentTask("maintenance", "one-off-migration", "0 0 0 1 1 ? 2027"),
                null);
        manager.setTaskStatus(TaskId.of("maintenance", "one-off-migration"), TaskStatus.SCHEDULED);
        manager.setTaskStatus(TaskId.of("maintenance", "one-off-migration"), TaskStatus.RUNNING);
        manager.setTaskStatus(TaskId.of("maintenance", "one-off-migration"), TaskStatus.FINISHED);

        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
            ((com.zaxxer.hikari.HikariDataSource) dataSource).close();
        }
        System.out.println("[populator] " + provider + " populated with 4 tasks and execution logs.");
    }

}
