package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;
import com.github.cronsmith.springapp.scheduler.jooq.JooqTaskManager;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.PersistentTask;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.ReflectiveTarget;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 
 * The scheduler driven against a persistent (JOOQ over H2) task manager, including the case only a
 * durable store can show: one scheduler is stopped and a fresh one started against the same
 * database, and it must pick up the tasks the first one left behind.
 * 
 * <p>
 * The task is a {@link PersistentTask}, whose whole definition is a database row, because a task
 * with in-process state cannot be rebuilt from storage. Its runs are observed through the durable
 * run counter rather than an in-memory field, for the same reason.
 * 
 * @Description: SchedulerJooqIntegrationTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class SchedulerJooqIntegrationTests {

    private static final long AWAIT_SECONDS = 20L;
    private static final String EVERY_SECOND = "* * * * * ?";

    private DataSource dataSource;

    @BeforeEach
    public void setUp() throws Exception {
        dataSource = DatabaseProvider.H2.createDataSource();
        DatabaseProvider.H2.initializeSchema(dataSource);
        ReflectiveTarget.resetCalls();
    }

    @AfterEach
    public void tearDown() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    private void awaitRunCount(TaskManager manager, TaskId taskId, long atLeast)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (manager.getTaskDetail(taskId, true).getRunCount() >= atLeast) {
                return;
            }
            Thread.sleep(50L);
        }
        org.junit.jupiter.api.Assertions.fail("run count did not reach " + atLeast + " within " + AWAIT_SECONDS
                + "s");
    }

    @Test
    public void testTaskRunsWithPersistentManager() throws Exception {
        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.setTaskManager(new JooqTaskManager(dataSource));
        scheduler.setTickDuration(200L);
        try {
            assertTrue(scheduler.schedule(
                    new PersistentTask(TaskId.DEFAULT_GROUP, "persisted", EVERY_SECOND), "p"));
            scheduler.start();
            awaitRunCount(new JooqTaskManager(dataSource), TaskId.of("persisted"), 3);
        } finally {
            scheduler.close();
        }

        // The run history is durable: a fresh manager over the same database still sees it.
        JooqTaskManager reader = new JooqTaskManager(dataSource);
        assertTrue(reader.findExecutionLogs(TaskId.of("persisted"), 100, 0).size() >= 3);
        assertTrue(ReflectiveTarget.getCalls() >= 3, "the body actually ran reflectively");
    }

    @Test
    public void testTaskIsRestoredByAFreshScheduler() throws Exception {
        // First scheduler: register a task, let it run, then stop without removing it, leaving a
        // live row in the database.
        TimeWheelScheduler first = new TimeWheelScheduler();
        first.setTaskManager(new JooqTaskManager(dataSource));
        first.setTickDuration(200L);
        first.schedule(new PersistentTask(TaskId.DEFAULT_GROUP, "survivor", EVERY_SECOND));
        first.start();
        awaitRunCount(new JooqTaskManager(dataSource), TaskId.of("survivor"), 1);
        first.close();

        JooqTaskManager afterStop = new JooqTaskManager(dataSource);
        TaskStatus statusAfterStop = afterStop.getTaskStatus(TaskId.of("survivor"));
        assertNotNull(statusAfterStop);
        long runsBefore = afterStop.getTaskDetail(TaskId.of("survivor"), true).getRunCount();

        // Second scheduler over the same database: restore must put the task back so it keeps
        // running, without it being scheduled again by hand.
        TimeWheelScheduler second = new TimeWheelScheduler();
        JooqTaskManager manager = new JooqTaskManager(dataSource);
        second.setTaskManager(manager);
        second.setTickDuration(200L);
        try {
            second.start();
            awaitRunCount(manager, TaskId.of("survivor"), runsBefore + 1);
        } finally {
            second.close();
        }
    }

}
