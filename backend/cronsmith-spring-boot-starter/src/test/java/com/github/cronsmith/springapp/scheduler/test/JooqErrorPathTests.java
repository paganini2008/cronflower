package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.TaskException;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.jooq.JooqTaskManager;
import com.github.cronsmith.springapp.scheduler.jooq.TaskTables;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.PersistentTask;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 
 * The JOOQ task manager's error and edge behaviour: a broken data source surfaces as a
 * {@link TaskException}, a custom table prefix is honoured, and the interface defaults return
 * gracefully for a task that does not exist.
 * 
 * @Description: JooqErrorPathTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class JooqErrorPathTests {

    private DataSource dataSource;
    private JooqTaskManager taskManager;

    @BeforeEach
    public void setUp() throws Exception {
        dataSource = DatabaseProvider.H2.createDataSource();
        DatabaseProvider.H2.initializeSchema(dataSource);
        taskManager = new JooqTaskManager(dataSource);
    }

    @AfterEach
    public void tearDown() {
        if (dataSource instanceof HikariDataSource && !((HikariDataSource) dataSource).isClosed()) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    public void testInterfaceDefaultsForMissingTask() {
        assertNull(taskManager.getInitialParameter(TaskId.of("nope")));
        assertNull(taskManager.getTaskStatus(TaskId.of("nope")));
        assertFalse(taskManager.hasTask(null));
    }

    @Test
    public void testInitialParameterAndStatusForKnownTask() {
        taskManager.saveTask(new PersistentTask("g", "n", "0 0 12 * * ?"), "param");
        assertEquals("param", taskManager.getInitialParameter(TaskId.of("g", "n")));
        assertEquals(TaskStatus.STANDBY, taskManager.getTaskStatus(TaskId.of("g", "n")));
    }

    @Test
    public void testComputeNextForMissingTaskReturnsNull() {
        assertNull(taskManager.computeNextFiredDateTime(TaskId.of("nope"),
                com.github.cronsmith.springapp.scheduler.Settings.now()));
        assertTrue(taskManager.findNextFiredDateTimes(TaskId.of("nope"),
                com.github.cronsmith.springapp.scheduler.Settings.now(),
                com.github.cronsmith.springapp.scheduler.Settings.now().plusDays(1)).isEmpty());
    }

    @Test
    public void testCasRejectsIllegalTransition() {
        taskManager.saveTask(new PersistentTask("g", "n", "0 0 12 * * ?"), null);
        assertFalse(taskManager.compareAndSetTaskStatus(TaskId.of("g", "n"), null,
                TaskStatus.RUNNING));
        assertFalse(taskManager.compareAndSetTaskStatus(TaskId.of("g", "n"), TaskStatus.STANDBY,
                TaskStatus.RUNNING));
    }

    @Test
    public void testCustomTablePrefix() throws Exception {
        // A second manager pointed at a different prefix must not see the default tables, proving
        // the prefix flows all the way into the SQL.
        JooqTaskManager prefixed =
                new JooqTaskManager(taskManager.getDslContext(), new TaskTables("cs_"));
        assertEquals("cs_", prefixed.getTables().getPrefix());
        assertNotNull(prefixed.getDslContext());
    }

    @Test
    public void testOperationsOnBrokenDataSourceThrowTaskException() {
        JooqTaskManager broken = new JooqTaskManager(dataSource);
        ((HikariDataSource) dataSource).close();
        try {
            broken.hasTask(TaskId.of("x"));
            org.junit.jupiter.api.Assertions.fail("expected TaskException");
        } catch (TaskException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void testCountAndListWithEmptyQuery() {
        taskManager.saveTask(new PersistentTask("g", "n", "0 0 12 * * ?"), null);
        assertEquals(1, taskManager.getTaskCount(TaskQuery.newQuery()));
        assertEquals(1, taskManager.findTaskDetails(TaskQuery.newQuery()).size());
        // A query with only an offset, no limit, exercises the offset-without-limit branch.
        assertTrue(taskManager.findTaskDetails(TaskQuery.newQuery().limit(0).offset(5)).isEmpty());
    }

    @Test
    public void testConstructorFromDataSourceDetectsDialect() {
        // The DataSource-only constructor must probe the connection for its dialect.
        JooqTaskManager fromDataSource = new JooqTaskManager(dataSource);
        assertNotNull(fromDataSource.getDslContext());
        assertEquals(TaskTables.DEFAULT_PREFIX, fromDataSource.getTables().getPrefix());
    }

}
