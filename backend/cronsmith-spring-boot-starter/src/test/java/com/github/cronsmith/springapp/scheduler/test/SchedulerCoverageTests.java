package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.cron.CronBuilder;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.InMemoryTaskManager;
import com.github.cronsmith.springapp.scheduler.MisfirePolicy;
import com.github.cronsmith.springapp.scheduler.Settings;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;
import com.github.cronsmith.springapp.scheduler.TimingWheelTaskQueue;
import com.github.cronsmith.springapp.scheduler.UpcomingTaskQueue;

/**
 * 
 * Fills in the scheduler's configuration surface and its misfire handling on restore, the paths the
 * happy-path end-to-end tests do not reach.
 * 
 * @Description: SchedulerCoverageTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class SchedulerCoverageTests {

    private TimeWheelScheduler scheduler;
    private TaskManager taskManager;

    @BeforeEach
    public void setUp() {
        taskManager = new InMemoryTaskManager();
        scheduler = new TimeWheelScheduler();
        scheduler.setTaskManager(taskManager);
        scheduler.setTickDuration(200L);
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
    }

    /**
     * 
     * A per-second counting task with a configurable misfire policy.
     * 
     * @Description: PolicyTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    static class PolicyTask implements Task {

        private final TaskId taskId;
        private final MisfirePolicy policy;
        final AtomicInteger runs = new AtomicInteger();

        PolicyTask(String name, MisfirePolicy policy) {
            this.taskId = TaskId.of(name);
            this.policy = policy;
        }

        @Override
        public TaskId getTaskId() {
            return taskId;
        }

        @Override
        public CronExpression getCronExpression() {
            return new CronBuilder().everySecond(1);
        }

        @Override
        public MisfirePolicy getMisfirePolicy() {
            return policy;
        }

        @Override
        public Object execute(String p) {
            return runs.incrementAndGet();
        }

    }

    @Test
    public void testConfigurationAccessors() {
        assertSame(taskManager, scheduler.getTaskManager());
        assertEquals(200L, scheduler.getTickDuration());
        scheduler.setZoneId(ZoneId.of("UTC"));
        assertEquals(ZoneId.of("UTC"), scheduler.getZoneId());
        scheduler.setMisfireThreshold(5000L);
        assertEquals(5000L, scheduler.getMisfireThreshold());
        assertNotNull(scheduler.getTaskQueue());
    }

    @Test
    public void testListenerManagement() {
        RecordingTaskListener listener = new RecordingTaskListener();
        scheduler.addTaskListener(listener);
        assertTrue(scheduler.getTaskListeners().contains(listener));
        scheduler.addTaskListener(null); // ignored
        scheduler.removeTaskListener(listener);
        assertFalse(scheduler.getTaskListeners().contains(listener));
    }

    @Test
    public void testCustomQueueIsUsed() {
        UpcomingTaskQueue custom = new TimingWheelTaskQueue(200L, 60);
        scheduler.setTaskQueue(custom);
        assertSame(custom, scheduler.getTaskQueue());
    }

    @Test
    public void testCannotSetTaskManagerAfterStart() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
        scheduler.start();
        scheduler.setTaskManager(new InMemoryTaskManager());
    
        });
    }

    @Test
    public void testCloseBeforeStartIsNoop() {
        scheduler.close();
        assertFalse(scheduler.isStarted());
    }

    @Test
    public void testScheduleByTaskIdAfterSave() {
        PolicyTask task = new PolicyTask("byid", MisfirePolicy.FIRE_ONCE_NOW);
        taskManager.saveTask(task, null);
        // The task is registered but not yet queued; scheduling it by id queues it.
        assertTrue(scheduler.schedule(TaskId.of("byid")));
        assertEquals(TaskStatus.SCHEDULED, taskManager.getTaskStatus(TaskId.of("byid")));
    }

    @Test
    public void testScheduleUnknownTaskIdReturnsFalse() {
        assertFalse(scheduler.schedule(TaskId.of("ghost")));
    }

    @Test
    public void testMisfireOnRestoreFireOnceNow() throws Exception {
        // Leave a task with a fire time well in the past, then start: the missed occurrence is run
        // once under the default policy, and the misfire is recorded.
        PolicyTask task = new PolicyTask("missed", MisfirePolicy.FIRE_ONCE_NOW);
        taskManager.saveTask(task, null);
        taskManager.setTaskStatus(TaskId.of("missed"), TaskStatus.SCHEDULED);
        taskManager.computeNextFiredDateTime(TaskId.of("missed"), Settings.now().minusMinutes(5));
        RecordingTaskListener listener = new RecordingTaskListener();
        scheduler.addTaskListener(listener);

        scheduler.start();
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() < deadline && task.runs.get() < 1) {
            Thread.sleep(50L);
        }
        assertTrue(task.runs.get() >= 1, "the missed occurrence runs once");
        assertTrue(listener.misfired.get() >= 1, "the misfire is reported");
        assertTrue(taskManager.getTaskDetail(TaskId.of("missed"), true).getMisfireCount() >= 1);
    }

    @Test
    public void testMisfireOnRestoreSkip() throws Exception {
        // Under SKIP the missed occurrence is dropped, but the task is still scheduled forward and
        // runs at its next future second.
        PolicyTask task = new PolicyTask("skipper", MisfirePolicy.SKIP);
        taskManager.saveTask(task, null);
        taskManager.setTaskStatus(TaskId.of("skipper"), TaskStatus.SCHEDULED);
        taskManager.computeNextFiredDateTime(TaskId.of("skipper"), Settings.now().minusMinutes(5));
        RecordingTaskListener listener = new RecordingTaskListener();
        scheduler.addTaskListener(listener);

        scheduler.start();
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() < deadline && task.runs.get() < 1) {
            Thread.sleep(50L);
        }
        assertTrue(task.runs.get() >= 1, "it still runs at the next future occurrence");
        assertTrue(listener.misfired.get() >= 1, "the misfire is reported");
    }

    @Test
    public void testIntervalTaskRuns() throws Exception {
        // A fixed-interval schedule rather than a cron one, to cover CRON.setInterval.
        //
        // CRON.setInterval anchors its start time in the system default zone, so the scheduler
        // driving it has to read the clock in that same zone; a UTC scheduler would place the first
        // fire time hours away and the task would never run in the test window. This mismatch is
        // real: an interval expression and its scheduler must agree on the zone.
        TaskManager manager = new InMemoryTaskManager();
        TimeWheelScheduler local = new TimeWheelScheduler();
        local.setTaskManager(manager);
        local.setTickDuration(200L);
        local.setZoneId(java.time.ZoneId.systemDefault());
        Task task = new Task() {
            @Override
            public TaskId getTaskId() {
                return TaskId.of("interval");
            }

            @Override
            public CronExpression getCronExpression() {
                return com.github.cronsmith.CRON.setInterval(1, TimeUnit.SECONDS);
            }

            @Override
            public Object execute(String p) {
                return "ran";
            }
        };
        try {
            assertTrue(local.schedule(task));
            local.start();
            long deadline = System.currentTimeMillis() + 15000L;
            while (System.currentTimeMillis() < deadline
                    && manager.getTaskDetail(TaskId.of("interval"), true).getRunCount() < 2) {
                Thread.sleep(50L);
            }
            assertTrue(manager.getTaskDetail(TaskId.of("interval"), true).getRunCount() >= 2);
        } finally {
            local.close();
        }
    }

}
