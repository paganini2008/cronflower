package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.jooq.JooqTaskManager;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.PersistentTask;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 
 * Runs the same {@link JooqTaskManager} contract against every supported database, so a difference
 * between dialects surfaces as a failure rather than as a surprise in production. H2 and SQLite run
 * embedded and always execute; MySQL and PostgreSQL execute only when their server is reachable, so
 * the suite stays green on a machine that has neither.
 * 
 * @Description: JooqTaskManagerTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class JooqTaskManagerTests {

    // Anchored to tomorrow's midnight rather than a fixed calendar date. saveTask() sync()s a task's
    // schedule to "now", so fire-time windows have to start after now to be deterministic whatever
    // day (and time of day) the suite runs; a hard-coded past date made these assertions drift.
    private static final LocalDateTime BASE =
            LocalDateTime.now().plusDays(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);

    static Stream<DatabaseProvider> databases() {
        return Stream.of(DatabaseProvider.H2, DatabaseProvider.SQLITE, DatabaseProvider.POSTGRESQL,
                DatabaseProvider.MYSQL);
    }

    private DataSource dataSource;
    private JooqTaskManager taskManager;

    private void setUpQuietly(DatabaseProvider provider) {
        try { setUp(provider); } catch (Exception e) { throw new RuntimeException(e); }
    }

    void setUp(DatabaseProvider provider) throws Exception {
        assumeTrue(provider.isAvailable(), provider + " is not reachable; skipping.");
        dataSource = provider.createDataSource();
        provider.initializeSchema(dataSource);
        taskManager = new JooqTaskManager(dataSource);
    }

    @AfterEach
    public void tearDown() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    private PersistentTask task(String name) {
        return new PersistentTask(TaskId.DEFAULT_GROUP, name, "0 0 12 * * ?");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testSaveAndGet(DatabaseProvider provider) {
        setUpQuietly(provider);
        TaskDetail saved = taskManager.saveTask(task("daily"), "param");
        assertNotNull(saved);
        assertEquals(TaskStatus.STANDBY, saved.getTaskStatus());
        assertEquals("param", saved.getInitialParameter());
        assertTrue(taskManager.hasTask(TaskId.of("daily")));

        TaskDetail fetched = taskManager.getTaskDetail(TaskId.of("daily"), true);
        assertEquals(TaskId.of("daily"), fetched.getTaskId());
        // The stored row rebuilds into a working task through the custom task factory.
        assertNotNull(fetched.getTask());
        assertEquals("0 0 12 * * ?", fetched.getTask().getCronExpression().toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testGetMissingReturnsNullOrThrows(DatabaseProvider provider) {
        setUpQuietly(provider);
        assertNull(taskManager.getTaskDetail(TaskId.of("nope"), false));
        assertFalse(taskManager.hasTask(TaskId.of("nope")));
        try {
            taskManager.getTaskDetail(TaskId.of("nope"), true);
            org.junit.jupiter.api.Assertions.fail("expected TaskDetailNotFoundException");
        } catch (com.github.cronsmith.springapp.scheduler.TaskDetailNotFoundException expected) {
            // expected
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testReSaveUpdatesInPlaceAndKeepsCounters(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("job"), "first");
        TaskExecutionLog runLog =
                new TaskExecutionLog(TaskId.of("job"), BASE).attempt(0).success(true);
        taskManager.recordExecution(runLog);
        assertEquals(1, taskManager.getTaskCount(TaskQuery.newQuery()));

        taskManager.saveTask(task("job"), "second");
        assertEquals(1, taskManager.getTaskCount(TaskQuery.newQuery()), "still one row");
        TaskDetail detail = taskManager.getTaskDetail(TaskId.of("job"), true);
        assertEquals("second", detail.getInitialParameter());
        assertEquals(1L, detail.getRunCount(), "run counter survives a re-save");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testRemove(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("gone"), null);
        assertNotNull(taskManager.removeTask(TaskId.of("gone")));
        assertFalse(taskManager.hasTask(TaskId.of("gone")));
        assertNull(taskManager.removeTask(TaskId.of("gone")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testStatusTransitionsFollowTheTable(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("job"), null);
        TaskId id = TaskId.of("job");
        assertTrue(taskManager.setTaskStatus(id, TaskStatus.SCHEDULED));
        assertEquals(TaskStatus.SCHEDULED, taskManager.getTaskStatus(id));
        // STANDBY cannot be reached from SCHEDULED via RUNNING skipped -- but SCHEDULED->STANDBY is
        // allowed, so this holds; an illegal jump is rejected below.
        assertFalse(taskManager.setTaskStatus(id, TaskStatus.NONE), "SCHEDULED cannot jump straight to NONE");
        assertTrue(taskManager.setTaskStatus(id, TaskStatus.RUNNING));
        assertTrue(taskManager.setTaskStatus(id, TaskStatus.FINISHED));
        assertFalse(taskManager.setTaskStatus(id, TaskStatus.SCHEDULED), "FINISHED is terminal");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testCompareAndSetStatusIsConditional(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("job"), null);
        TaskId id = TaskId.of("job");
        taskManager.setTaskStatus(id, TaskStatus.SCHEDULED);
        assertFalse(taskManager.compareAndSetTaskStatus(id, TaskStatus.RUNNING, TaskStatus.FINISHED), "wrong expected state");
        assertTrue(taskManager.compareAndSetTaskStatus(id, TaskStatus.SCHEDULED,
                TaskStatus.RUNNING));
        assertEquals(TaskStatus.RUNNING, taskManager.getTaskStatus(id));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testComputeNextFiredDateTimeIsPersisted(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("daily"), null);
        LocalDateTime next = taskManager.computeNextFiredDateTime(TaskId.of("daily"), BASE);
        assertNotNull(next);
        assertEquals(12, next.getHour());
        TaskDetail detail = taskManager.getTaskDetail(TaskId.of("daily"), true);
        assertEquals(next, detail.getNextFiredDateTime());
        assertEquals(BASE, detail.getPreviousFiredDateTime());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testFindNextFiredDateTimes(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("daily"), null);
        List<LocalDateTime> times = taskManager.findNextFiredDateTimes(TaskId.of("daily"),
                BASE, BASE.plusDays(3));
        assertEquals(3, times.size(), "three noons in three days");
        times.forEach(t -> assertEquals(12, t.getHour()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testFindUpcomingTasksBetween(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("a"), null);
        taskManager.saveTask(task("b"), null);
        taskManager.setTaskStatus(TaskId.of("a"), TaskStatus.SCHEDULED);
        taskManager.setTaskStatus(TaskId.of("b"), TaskStatus.SCHEDULED);
        taskManager.computeNextFiredDateTime(TaskId.of("a"), BASE);
        taskManager.computeNextFiredDateTime(TaskId.of("b"), BASE);
        List<TaskId> upcoming =
                taskManager.findUpcomingTasksBetween(BASE, BASE.plusDays(1).plusHours(1));
        assertEquals(2, upcoming.size());
        assertTrue(upcoming.contains(TaskId.of("a")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testQueryFilteringAndPaging(DatabaseProvider provider) {
        setUpQuietly(provider);
        for (int i = 0; i < 15; i++) {
            taskManager.saveTask(new PersistentTask("groupX", "task-" + i, "0 0 12 * * ?"), null);
        }
        taskManager.saveTask(new PersistentTask("groupY", "other", "0 0 12 * * ?"), null);

        assertEquals(16, taskManager.getTaskCount(TaskQuery.newQuery()));
        assertEquals(15, taskManager.getTaskCount(TaskQuery.newQuery().group("groupX")));
        assertEquals(1, taskManager.getTaskCount(TaskQuery.newQuery().name("other")));

        List<TaskDetail> firstPage =
                taskManager.findTaskDetails(TaskQuery.newQuery().group("groupX").limit(10));
        assertEquals(10, firstPage.size());
        List<TaskDetail> secondPage = taskManager
                .findTaskDetails(TaskQuery.newQuery().group("groupX").limit(10).offset(10));
        assertEquals(5, secondPage.size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testQueryByStatus(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("a"), null);
        taskManager.saveTask(task("b"), null);
        taskManager.setTaskStatus(TaskId.of("a"), TaskStatus.SCHEDULED);
        assertEquals(1,
                taskManager.getTaskCount(TaskQuery.newQuery().statuses(TaskStatus.SCHEDULED)));
        assertEquals(1,
                taskManager.getTaskCount(TaskQuery.newQuery().statuses(TaskStatus.STANDBY)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testExecutionLogsAndCounters(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("job"), null);
        TaskId id = TaskId.of("job");
        taskManager.recordExecution(
                new TaskExecutionLog(id, BASE).attempt(0).success(false).elapsed(5)
                        .error(new IllegalStateException("boom")));
        taskManager.recordExecution(
                new TaskExecutionLog(id, BASE).attempt(1).success(true).elapsed(7)
                        .returnValue("ok"));

        List<TaskExecutionLog> logs = taskManager.findExecutionLogs(id, 10, 0);
        assertEquals(2, logs.size());
        // Newest attempt first.
        assertTrue(logs.get(0).isSuccess());
        assertEquals("ok", logs.get(0).getReturnValue());
        assertNotNull(logs.get(1).getErrorDetail());

        TaskDetail detail = taskManager.getTaskDetail(id, true);
        assertEquals(2L, detail.getRunCount());
        assertEquals(1L, detail.getFailureCount());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testRecordMisfire(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("job"), null);
        taskManager.recordMisfire(TaskId.of("job"), BASE);
        taskManager.recordMisfire(TaskId.of("job"), BASE.plusMinutes(1));
        assertEquals(2L, taskManager.getTaskDetail(TaskId.of("job"), true).getMisfireCount());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testCronExpressionSurvivesDatabaseRoundTrip(DatabaseProvider provider) {
        setUpQuietly(provider);
        // The schedule is stored as a BLOB and read straight back out. Every dialect stores binary
        // differently, so this is where a BLOB-handling difference between them would show up.
        String[] crons = {"0 0 12 * * ?", "0 0/15 * * * ?", "0 0 0 1 * ?",
                "0 30 9 ? * MON-FRI", "*/30 * * * * ?"};
        for (int i = 0; i < crons.length; i++) {
            String cron = crons[i];
            taskManager.saveTask(new PersistentTask(TaskId.DEFAULT_GROUP, "cron-" + i, cron), null);
            CronExpression restored =
                    taskManager.getTaskDetail(TaskId.of("cron-" + i), true).getTask()
                            .getCronExpression();
            assertEquals(CRON.parse(cron).list(BASE, BASE.plusDays(3)), restored.list(BASE, BASE.plusDays(3)), "fire times diverged after a DB round trip for: " + cron);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testComputedFireTimesMatchOriginalAcrossDatabase(DatabaseProvider provider) {
        setUpQuietly(provider);
        // Walk the schedule forward through the database: each computeNextFiredDateTime persists the
        // advanced expression and reads it back, so the sequence must match the in-memory original
        // step for step.
        taskManager.saveTask(new PersistentTask(TaskId.DEFAULT_GROUP, "walk", "0 0/15 * * * ?"),
                null);
        CronExpression reference = CRON.parse("0 0/15 * * * ?");
        LocalDateTime dbCursor = BASE;
        LocalDateTime refCursor = BASE;
        for (int i = 0; i < 8; i++) {
            dbCursor = taskManager.computeNextFiredDateTime(TaskId.of("walk"), dbCursor);
            refCursor = reference.getNextFiredDateTime(refCursor);
            assertEquals(refCursor, dbCursor, "step " + i + " diverged through the database");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testStoredNextFiredDateTimeReadsBackExactly(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("precise"), null);
        LocalDateTime next = taskManager.computeNextFiredDateTime(TaskId.of("precise"), BASE);
        // Re-read from a fresh manager instance so nothing is cached in memory.
        JooqTaskManager reader = new JooqTaskManager(dataSource);
        assertEquals(next,
                reader.getTaskDetail(TaskId.of("precise"), true).getNextFiredDateTime());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    public void testRestoreHandsBackLiveTasks(DatabaseProvider provider) {
        setUpQuietly(provider);
        taskManager.saveTask(task("live"), null);
        taskManager.saveTask(task("done"), null);
        taskManager.setTaskStatus(TaskId.of("live"), TaskStatus.SCHEDULED);
        taskManager.computeNextFiredDateTime(TaskId.of("live"), BASE);
        taskManager.setTaskStatus(TaskId.of("done"), TaskStatus.FINISHED);

        List<TaskId> restored = new java.util.ArrayList<>();
        taskManager.restoreTasks((taskId, next) -> restored.add(taskId));
        assertEquals(1, restored.size());
        assertEquals(TaskId.of("live"), restored.get(0));
        // A restored task is put back to standby, ready for the scheduler to pick up.
        assertEquals(TaskStatus.STANDBY, taskManager.getTaskStatus(TaskId.of("live")));
    }

}
