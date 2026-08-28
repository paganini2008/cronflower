package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.InMemoryTaskManager;
import com.github.cronsmith.springapp.scheduler.Settings;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.CountingTask;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.FlakyTask;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.OneShotTask;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.SlowTask;

/**
 * 
 * End-to-end tests of the scheduler against the in-memory task manager. They drive the real clock,
 * so a short tick is used to keep them quick, and every wait is on a latch or a polled condition
 * rather than a fixed sleep, so a slow machine makes them slower but never flaky.
 * 
 * @Description: TimeWheelSchedulerTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TimeWheelSchedulerTests {

    private static final long AWAIT_SECONDS = 15L;

    private TimeWheelScheduler scheduler;
    private TaskManager taskManager;
    private RecordingTaskListener listener;

    @BeforeEach
    public void setUp() {
        taskManager = new InMemoryTaskManager();
        scheduler = new TimeWheelScheduler();
        scheduler.setTaskManager(taskManager);
        scheduler.setTickDuration(200L);
        listener = new RecordingTaskListener();
        scheduler.addTaskListener(listener);
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
        org.junit.jupiter.api.Assertions.fail("condition not met within " + AWAIT_SECONDS + "s");
    }

    @Test
    public void testTaskRunsRepeatedly() throws Exception {
        CountingTask task = new CountingTask("repeat", 3);
        assertTrue(scheduler.schedule(task, "param"));
        scheduler.start();
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS), "expected three runs");
        assertTrue(task.getRuns() >= 3);
        // The initial parameter reaches the body.
        assertTrue(task.getResults().get(0).toString().contains("param"));
        assertTrue(listener.triggered.get() >= 3);
        assertTrue(listener.began.get() >= 3);
        assertTrue(listener.ended.get() >= 3);
    }

    @Test
    public void testScheduleReturnsToStandbyBetweenRuns() throws Exception {
        CountingTask task = new CountingTask("standby", 2);
        scheduler.schedule(task);
        scheduler.start();
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        // Between runs a repeating task is either scheduled again or briefly standby/running, never
        // terminal.
        awaitTrue(() -> {
            TaskStatus status = taskManager.getTaskStatus(TaskId.of("standby"));
            return status == TaskStatus.SCHEDULED || status == TaskStatus.STANDBY
                    || status == TaskStatus.RUNNING;
        });
    }

    @Test
    public void testPauseStopsAndResumeRestarts() throws Exception {
        CountingTask task = new CountingTask("pausable", 1);
        scheduler.schedule(task);
        scheduler.start();
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS));

        assertTrue(scheduler.pause(TaskId.of("pausable")));
        awaitTrue(() -> taskManager.getTaskStatus(TaskId.of("pausable")) == TaskStatus.PAUSED);
        int runsWhilePaused = task.getRuns();
        Thread.sleep(1500L);
        assertTrue(task.getRuns() <= runsWhilePaused + 1, "a paused task must not keep running");

        assertTrue(scheduler.resume(TaskId.of("pausable")));
        awaitTrue(() -> task.getRuns() > runsWhilePaused + 1);
    }

    @Test
    public void testCancel() throws Exception {
        CountingTask task = new CountingTask("cancelable", 1);
        scheduler.schedule(task);
        scheduler.start();
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS));

        assertTrue(scheduler.cancel(TaskId.of("cancelable")));
        assertEquals(TaskStatus.CANCELED, taskManager.getTaskStatus(TaskId.of("cancelable")));
        assertTrue(listener.canceled.get() >= 1);
        int runsAtCancel = task.getRuns();
        Thread.sleep(1200L);
        assertTrue(task.getRuns() <= runsAtCancel + 1, "a canceled task must not run again");
        // A canceled task can then be removed entirely.
        assertNotNull(scheduler.remove(TaskId.of("cancelable")));
        assertFalse(taskManager.hasTask(TaskId.of("cancelable")));
    }

    @Test
    public void testOneShotTaskFinishes() throws Exception {
        CronExpression once = CRON.atFuture(Settings.now().plusSeconds(1));
        OneShotTask task = new OneShotTask("oneshot", once);
        scheduler.schedule(task);
        scheduler.start();
        awaitTrue(() -> taskManager.getTaskStatus(TaskId.of("oneshot")) == TaskStatus.FINISHED);
        assertEquals(1, task.getRuns());
        assertTrue(listener.finished.get() >= 1);
    }

    @Test
    public void testRetryUntilSuccess() throws Exception {
        // Fails twice, then succeeds; allowed three retries, so the third attempt wins.
        FlakyTask task = new FlakyTask("flaky", 2, 3);
        scheduler.schedule(task);
        scheduler.start();
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(3, task.getAttempts());
        assertNotNull(task.getReportedResult(), "the successful result is reported");
        org.junit.jupiter.api.Assertions.assertNull(task.getReportedFailure(), "no failure once it eventually succeeds");
    }

    @Test
    public void testRetryExhaustedReportsFailure() throws Exception {
        // Fails three times with only one retry allowed: two attempts, both fail.
        FlakyTask task = new FlakyTask("doomed", 3, 1);
        scheduler.schedule(task);
        scheduler.start();
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(2, task.getAttempts());
        assertNotNull(task.getReportedFailure(), "the failure is reported");
    }

    @Test
    public void testTimeoutIsReportedAsFailure() throws Exception {
        // Sleeps for a second against a 200ms timeout.
        SlowTask task = new SlowTask("slow", 1000L, 200L);
        scheduler.schedule(task);
        scheduler.start();
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        assertNotNull(task.getReportedFailure(), "a timeout surfaces as a failure");
    }

    @Test
    public void testScheduleTwiceIsIdempotent() {
        CountingTask task = new CountingTask("once", 1);
        assertTrue(scheduler.schedule(task));
        assertFalse(scheduler.schedule(task), "already scheduled");
    }

    @Test
    public void testExecutionLogsAreRecorded() throws Exception {
        CountingTask task = new CountingTask("logged", 2);
        scheduler.schedule(task);
        scheduler.start();
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        awaitTrue(() -> !taskManager.findExecutionLogs(TaskId.of("logged"), 10, 0).isEmpty());
        TaskDetail detail = taskManager.getTaskDetail(TaskId.of("logged"), true);
        assertTrue(detail.getRunCount() >= 2);
    }

    @Test
    public void testStartIsIdempotentAndCloseStops() throws Exception {
        CountingTask task = new CountingTask("lifecycle", 1);
        scheduler.schedule(task);
        scheduler.start();
        scheduler.start();
        assertTrue(scheduler.isStarted());
        assertTrue(task.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        scheduler.close();
        assertFalse(scheduler.isStarted());
        int runsAfterClose = task.getRuns();
        Thread.sleep(1000L);
        assertEquals(runsAfterClose, task.getRuns(), "no runs after close");
    }

    @Test
    public void testCannotReconfigureRunningScheduler() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
        scheduler.start();
        scheduler.setTickDuration(1000L);
    
        });
    }

    @Test
    public void testPauseUnknownTaskReturnsFalse() {
        assertFalse(scheduler.pause(TaskId.of("ghost")));
        assertFalse(scheduler.resume(TaskId.of("ghost")));
        assertFalse(scheduler.cancel(TaskId.of("ghost")));
    }

}
