package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.MisfirePolicy;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskStatus;

/**
 * 
 * Small value types: ids, the query builder, the execution log, and the misfire enum.
 * 
 * @Description: TaskModelTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TaskModelTests {

    @Test
    public void testTaskIdEqualityAndHash() {
        assertEquals(TaskId.of("a"), TaskId.of("default", "a"));
        assertEquals(TaskId.of("g", "n"), TaskId.of("g", "n"));
        assertEquals(TaskId.of("g", "n").hashCode(), TaskId.of("g", "n").hashCode());
        assertNotEquals(TaskId.of("g", "n"), TaskId.of("g", "m"));
        assertNotEquals(TaskId.of("g", "n"), "not an id");
    }

    @Test
    public void testTaskIdToString() {
        assertEquals("g#n", TaskId.of("g", "n").toString());
    }

    @Test
    public void testTaskIdRejectsBlankName() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        TaskId.of("g", "");
    
        });
    }

    @Test
    public void testTaskIdRejectsBlankGroup() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        TaskId.of("", "n");
    
        });
    }

    @Test
    public void testQueryDefaults() {
        TaskQuery query = TaskQuery.newQuery();
        assertEquals(TaskQuery.DEFAULT_LIMIT, query.getLimit());
        assertEquals(0, query.getOffset());
        assertTrue(query.getStatuses().isEmpty());
    }

    @Test
    public void testQueryBuilder() {
        TaskQuery query = TaskQuery.newQuery().group("g").name("n").taskClass("C").limit(5)
                .offset(10).statuses(TaskStatus.SCHEDULED, TaskStatus.RUNNING);
        assertEquals("g", query.getTaskGroup());
        assertEquals("n", query.getTaskName());
        assertEquals("C", query.getTaskClass());
        assertEquals(5, query.getLimit());
        assertEquals(10, query.getOffset());
        assertEquals(2, query.getStatuses().size());
    }

    @Test
    public void testQueryNegativeOffsetBecomesZero() {
        assertEquals(0, TaskQuery.newQuery().offset(-5).getOffset());
    }

    @Test
    public void testQueryStatusesFromCollection() {
        TaskQuery query = TaskQuery.newQuery().statuses(Arrays.asList(TaskStatus.PAUSED, null));
        assertEquals(1, query.getStatuses().size());
        assertTrue(query.getStatuses().contains(TaskStatus.PAUSED));
    }

    @Test
    public void testExecutionLogTruncatesLongText() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            huge.append('x');
        }
        TaskExecutionLog log = new TaskExecutionLog(TaskId.of("t"), LocalDateTime.now())
                .returnValue(huge.toString());
        assertEquals(TaskExecutionLog.MAX_TEXT_LENGTH, log.getReturnValue().length());
    }

    @Test
    public void testExecutionLogErrorMarksFailure() {
        TaskExecutionLog log = new TaskExecutionLog(TaskId.of("t"), LocalDateTime.now())
                .success(true).error(new IllegalStateException("boom"));
        assertFalse(log.isSuccess());
        assertTrue(log.getErrorDetail().contains("boom"));
    }

    @Test
    public void testExecutionLogNullReturnValue() {
        TaskExecutionLog log =
                new TaskExecutionLog(TaskId.of("t"), LocalDateTime.now()).returnValue(null);
        assertNull(log.getReturnValue());
    }

    @Test
    public void testMisfirePolicyValues() {
        assertEquals("SKIP", MisfirePolicy.SKIP.getValue());
        assertEquals("FIRE_ALL", MisfirePolicy.FIRE_ALL.getRepr());
        assertEquals(3, MisfirePolicy.values().length);
    }

}
