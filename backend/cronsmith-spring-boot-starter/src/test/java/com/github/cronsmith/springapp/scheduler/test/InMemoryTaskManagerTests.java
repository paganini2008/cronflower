package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.cron.CronBuilder;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.InMemoryTaskManager;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskDetailNotFoundException;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskStatus;

/**
 * 
 * Exercises the in-memory task manager on its own, including the paths that only it has: the log
 * ring buffer and the object-identity storage that the JOOQ manager cannot share.
 * 
 * @Description: InMemoryTaskManagerTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class InMemoryTaskManagerTests {

    // Anchored to tomorrow's midnight, not a fixed calendar date: saveTask() sync()s the schedule to
    // "now", so a deterministic fire-time window has to start after now regardless of the run date.
    private static final LocalDateTime BASE =
            LocalDateTime.now().plusDays(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);

    private InMemoryTaskManager taskManager;

    @BeforeEach
    public void setUp() {
        taskManager = new InMemoryTaskManager();
    }

    /**
     * 
     * A plain daily task the manager stores by reference.
     * 
     * @Description: DailyTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    static class DailyTask implements Task {

        private final TaskId taskId;
        private final String cron;

        DailyTask(String name, String cron) {
            this.taskId = TaskId.of(name);
            this.cron = cron;
        }

        @Override
        public TaskId getTaskId() {
            return taskId;
        }

        @Override
        public CronExpression getCronExpression() {
            return com.github.cronsmith.CRON.parse(cron);
        }

        @Override
        public Object execute(String initialParameter) {
            return "ran";
        }

    }

    private DailyTask task(String name) {
        return new DailyTask(name, "0 0 12 * * ?");
    }

    @Test
    public void testSaveGetAndReference() {
        DailyTask task = task("daily");
        TaskDetail detail = taskManager.saveTask(task, "p");
        assertEquals(TaskStatus.STANDBY, detail.getTaskStatus());
        assertEquals("p", detail.getInitialParameter());
        // Stored by reference: the very same object comes back.
        assertTrue(task == taskManager.getTaskDetail(TaskId.of("daily"), true).getTask());
    }

    @Test
    public void testInitialParameterFallsBackToTaskDefault() {
        taskManager.saveTask(new CronBuilderTask("x"), null);
        assertEquals("default-param", taskManager.getInitialParameter(TaskId.of("x")));
    }

    static class CronBuilderTask implements Task {
        private final TaskId taskId;

        CronBuilderTask(String name) {
            this.taskId = TaskId.of(name);
        }

        @Override
        public TaskId getTaskId() {
            return taskId;
        }

        @Override
        public CronExpression getCronExpression() {
            return new CronBuilder().everySecond(30);
        }

        @Override
        public String getInitialParameter() {
            return "default-param";
        }

        @Override
        public Object execute(String initialParameter) {
            return null;
        }
    }

    @Test
    public void testMissingTask() {
        assertNull(taskManager.getTaskDetail(TaskId.of("nope"), false));
        assertFalse(taskManager.hasTask(TaskId.of("nope")));
        assertNull(taskManager.getTaskStatus(TaskId.of("nope")));
    }

    @Test
    public void testMissingTaskThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(TaskDetailNotFoundException.class, () -> {
        taskManager.getTaskDetail(TaskId.of("nope"), true);
    
        });
    }

    @Test
    public void testReSaveResetsStateButKeepsCounters() {
        taskManager.saveTask(task("job"), "one");
        taskManager.recordExecution(new TaskExecutionLog(TaskId.of("job"), BASE).success(true));
        taskManager.setTaskStatus(TaskId.of("job"), TaskStatus.SCHEDULED);

        TaskDetail again = taskManager.saveTask(task("job"), "two");
        assertEquals(TaskStatus.STANDBY, again.getTaskStatus());
        assertEquals("two", again.getInitialParameter());
        assertEquals(1L, again.getRunCount());
    }

    @Test
    public void testStatusTransitionTableIsEnforced() {
        taskManager.saveTask(task("job"), null);
        TaskId id = TaskId.of("job");
        assertFalse(taskManager.setTaskStatus(id, TaskStatus.RUNNING), "STANDBY cannot jump to RUNNING");
        assertTrue(taskManager.setTaskStatus(id, TaskStatus.SCHEDULED));
        assertTrue(taskManager.setTaskStatus(id, TaskStatus.RUNNING));
        assertTrue(taskManager.compareAndSetTaskStatus(id, TaskStatus.RUNNING,
                TaskStatus.STANDBY));
        assertFalse(taskManager.compareAndSetTaskStatus(id, TaskStatus.RUNNING,
                TaskStatus.STANDBY));
    }

    @Test
    public void testComputeNextAndFindTimes() {
        taskManager.saveTask(task("daily"), null);
        LocalDateTime next = taskManager.computeNextFiredDateTime(TaskId.of("daily"), BASE);
        assertEquals(12, next.getHour());
        assertEquals(next, taskManager.getTaskDetail(TaskId.of("daily"), true)
                .getNextFiredDateTime());

        List<LocalDateTime> times =
                taskManager.findNextFiredDateTimes(TaskId.of("daily"), BASE, BASE.plusDays(2));
        assertEquals(2, times.size());
    }

    @Test
    public void testFindNextFiredDateTimesOfUnavailableTaskIsEmpty() {
        taskManager.saveTask(task("daily"), null);
        taskManager.setTaskStatus(TaskId.of("daily"), TaskStatus.CANCELED);
        assertTrue(taskManager.findNextFiredDateTimes(TaskId.of("daily"), BASE, BASE.plusDays(2))
                .isEmpty());
    }

    @Test
    public void testFindUpcomingTasksBetween() {
        taskManager.saveTask(task("a"), null);
        taskManager.setTaskStatus(TaskId.of("a"), TaskStatus.SCHEDULED);
        taskManager.computeNextFiredDateTime(TaskId.of("a"), BASE);
        List<TaskId> upcoming =
                taskManager.findUpcomingTasksBetween(BASE, BASE.plusDays(1).plusHours(1));
        assertEquals(1, upcoming.size());
    }

    @Test
    public void testQueryFilteringAndPaging() {
        for (int i = 0; i < 12; i++) {
            taskManager.saveTask(new DailyTask("task-" + i, "0 0 12 * * ?"), null);
        }
        assertEquals(12, taskManager.getTaskCount(TaskQuery.newQuery()));
        assertEquals(1, taskManager.getTaskCount(TaskQuery.newQuery().name("task-5")));
        assertEquals(5, taskManager.findTaskDetails(TaskQuery.newQuery().limit(5)).size());
        assertEquals(2,
                taskManager.findTaskDetails(TaskQuery.newQuery().limit(5).offset(10)).size());
    }

    @Test
    public void testQueryByClassName() {
        taskManager.saveTask(task("a"), null);
        assertEquals(1, taskManager.getTaskCount(TaskQuery.newQuery().taskClass("DailyTask")));
        assertEquals(0, taskManager.getTaskCount(TaskQuery.newQuery().taskClass("Nonexistent")));
    }

    @Test
    public void testExecutionLogRingBufferEvictsOldest() {
        InMemoryTaskManager small = new InMemoryTaskManager(3);
        small.saveTask(task("job"), null);
        for (int i = 0; i < 5; i++) {
            small.recordExecution(
                    new TaskExecutionLog(TaskId.of("job"), BASE.plusSeconds(i)).attempt(i)
                            .success(true));
        }
        List<TaskExecutionLog> logs = small.findExecutionLogs(TaskId.of("job"), 10, 0);
        assertEquals(3, logs.size(), "capacity is capped at three");
        // Newest first: attempts 4, 3, 2 survive.
        assertEquals(4, logs.get(0).getAttempt());
        assertEquals(2, logs.get(2).getAttempt());
    }

    @Test
    public void testExecutionLogsForUnknownTaskIsEmpty() {
        assertTrue(taskManager.findExecutionLogs(TaskId.of("nope"), 10, 0).isEmpty());
    }

    @Test
    public void testRemoveDropsLogsToo() {
        taskManager.saveTask(task("job"), null);
        taskManager.recordExecution(new TaskExecutionLog(TaskId.of("job"), BASE).success(true));
        taskManager.removeTask(TaskId.of("job"));
        assertTrue(taskManager.findExecutionLogs(TaskId.of("job"), 10, 0).isEmpty());
    }

    @Test
    public void testMisfireCounter() {
        taskManager.saveTask(task("job"), null);
        taskManager.recordMisfire(TaskId.of("job"), BASE);
        assertEquals(1L, taskManager.getTaskDetail(TaskId.of("job"), true).getMisfireCount());
    }

    @Test
    public void testRestoreSkipsUnavailableTasks() {
        taskManager.saveTask(task("live"), null);
        taskManager.saveTask(task("paused"), null);
        taskManager.setTaskStatus(TaskId.of("paused"), TaskStatus.PAUSED);

        List<TaskId> restored = new java.util.ArrayList<>();
        taskManager.restoreTasks((id, next) -> restored.add(id));
        assertEquals(1, restored.size());
        assertEquals(TaskId.of("live"), restored.get(0));
    }

}
