package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;
import com.github.cronsmith.springapp.scheduler.jooq.JooqTaskManager;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 
 * A custom task run for real, the way a persisted one is: it is stored as a row, torn down, rebuilt
 * from that row by the factory, and then driven to completion by the scheduler reaching its body
 * reflectively. This is the end-to-end path a task takes after a restart, exercised in one test.
 * 
 * @Description: CustomTaskSchedulingTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class CustomTaskSchedulingTests {

    /** Counts invocations across rebuilds, since a rebuilt task is a different instance each time. */
    static final AtomicInteger INVOCATIONS = new AtomicInteger();
    static volatile CountDownLatch latch;
    static volatile String lastParameter;

    private DataSource dataSource;

    @BeforeEach
    public void setUp() throws Exception {
        dataSource = DatabaseProvider.H2.createDataSource();
        DatabaseProvider.H2.initializeSchema(dataSource);
        INVOCATIONS.set(0);
        // Wait for four body entries: recordExecution runs after a body returns, so a
        // fourth entry guarantees the first three have completed and been persisted.
        latch = new CountDownLatch(4);
        lastParameter = null;
    }

    @AfterEach
    public void tearDown() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    /**
     * 
     * The target a custom task points at: a plain business object, unaware of the scheduler, called
     * by name. This is the shape real user code takes.
     * 
     * @Description: BusinessService
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class BusinessService {

        public String runReport(String parameter) {
            INVOCATIONS.incrementAndGet();
            lastParameter = parameter;
            latch.countDown();
            return "report done: " + parameter;
        }

    }

    /**
     * 
     * A custom task pointing at {@link BusinessService#runReport}, whose whole definition is data so
     * it survives being stored and rebuilt.
     * 
     * @Description: ReportTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class ReportTask implements Task {

        @Override
        public TaskId getTaskId() {
            return TaskId.of("reports", "daily-report");
        }

        @Override
        public CronExpression getCronExpression() {
            return CRON.parse("* * * * * ?");
        }

        @Override
        public String getInitialParameter() {
            return "Q3";
        }

        @Override
        public Object execute(String initialParameter) {
            return new BusinessService().runReport(initialParameter);
        }

    }

    @Test
    public void testCustomTaskRunsThroughReflectionAndPersistence() throws Exception {
        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.setTaskManager(new JooqTaskManager(dataSource));
        scheduler.setTickDuration(200L);
        try {
            // Register the custom task and let the scheduler drive it. On each run the scheduler
            // rebuilds the task from its stored row and reaches BusinessService.runReport by name.
            assertTrue(scheduler.schedule(new ReportTask(), "Q3"));
            scheduler.start();
            assertTrue(latch.await(20L, TimeUnit.SECONDS), "custom task should run at least three times");
        } finally {
            scheduler.close();
        }
        assertTrue(INVOCATIONS.get() >= 3);
        org.junit.jupiter.api.Assertions.assertEquals("Q3", lastParameter, "the initial parameter reaches the target");

        // The runs are durable and reflect the reflective invocations.
        JooqTaskManager reader = new JooqTaskManager(dataSource);
        assertTrue(reader.getTaskDetail(TaskId.of("reports", "daily-report"), true)
                .getRunCount() >= 3);
    }

    @Test
    public void testCustomTaskRunsWithInMemoryManager() throws Exception {
        // The same custom task, driven against the in-memory manager, which keeps the task by
        // reference rather than rebuilding it.
        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.setTickDuration(200L);
        try {
            scheduler.schedule(new ReportTask(), "Q3");
            scheduler.start();
            assertTrue(latch.await(20L, TimeUnit.SECONDS));
        } finally {
            scheduler.close();
        }
        assertTrue(INVOCATIONS.get() >= 3);
    }

}
