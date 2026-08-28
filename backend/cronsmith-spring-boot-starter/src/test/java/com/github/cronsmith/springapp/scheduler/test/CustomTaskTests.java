package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.BeanReflectionTask;
import com.github.cronsmith.springapp.scheduler.DefaultTaskFactory;
import com.github.cronsmith.springapp.scheduler.MisfirePolicy;
import com.github.cronsmith.springapp.scheduler.TaskException;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskReflectionUtils;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.ReflectiveTarget;

/**
 *
 * Covers how a bean-reflection task rebuilt from a stored row reads each of its properties,
 * especially the several forms the schedule can be stored in.
 *
 * @Description: CustomTaskTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class CustomTaskTests {

    private Map<String, Object> baseRecord() {
        Map<String, Object> record = new HashMap<>();
        record.put("taskGroup", "g");
        record.put("taskName", "n");
        record.put("taskClass", ReflectiveTarget.class.getName());
        record.put("taskMethod", "execute");
        return record;
    }

    private BeanReflectionTask bean(Map<String, Object> record) {
        return (BeanReflectionTask) TaskReflectionUtils.getTaskFactory()
                .createBeanReflectionTask(record);
    }

    @Test
    public void testScheduleFromExpressionObject() {
        Map<String, Object> record = baseRecord();
        CronExpression expr = CRON.parse("0 0 12 * * ?");
        record.put("cronExpression", expr);
        assertEquals(expr, bean(record).getCronExpression());
    }

    @Test
    public void testScheduleFromBytes() {
        Map<String, Object> record = baseRecord();
        record.put("cronExpression", CRON.parse("0 0 12 * * ?").serialize());
        assertEquals("0 0 12 * * ?", bean(record).getCronExpression().toString());
    }

    @Test
    public void testScheduleFromText() {
        Map<String, Object> record = baseRecord();
        record.put("cronExpression", "0 0/5 * * * ?");
        assertEquals("0 0/5 * * * ?", bean(record).getCronExpression().toString());
    }

    @Test
    public void testScheduleFromCronColumnFallback() {
        Map<String, Object> record = baseRecord();
        record.put("cron", "0 0 12 * * ?");
        assertNotNull(bean(record).getCronExpression());
    }

    @Test
    public void testScheduleFromLocalDateTime() {
        Map<String, Object> record = baseRecord();
        record.put("cronExpression", LocalDateTime.now().plusDays(1));
        assertNotNull(bean(record).getCronExpression());
    }

    @Test
    public void testScheduleFromLocalDate() {
        Map<String, Object> record = baseRecord();
        record.put("cronExpression", LocalDate.now().plusDays(1));
        assertNotNull(bean(record).getCronExpression());
    }

    @Test
    public void testScheduleFromLocalTime() {
        Map<String, Object> record = baseRecord();
        record.put("cronExpression", LocalTime.of(23, 30));
        assertNotNull(bean(record).getCronExpression());
    }

    @Test
    public void testMissingScheduleThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(TaskException.class, () -> {
        bean(baseRecord()).getCronExpression();
    
        });
    }

    @Test
    public void testIdentityAndMetadata() {
        Map<String, Object> record = baseRecord();
        record.put("description", "desc");
        record.put("timeout", 5000L);
        record.put("maxRetryCount", 3);
        record.put("initialParameter", "p");
        record.put("misfirePolicy", "SKIP");
        BeanReflectionTask task = bean(record);
        assertEquals(TaskId.of("g", "n"), task.getTaskId());
        assertEquals(ReflectiveTarget.class.getName(), task.getTaskClassName());
        assertEquals("execute", task.getTaskMethodName());
        assertEquals("desc", task.getDescription());
        assertEquals(5000L, task.getTimeout());
        assertEquals(3, task.getMaxRetryCount());
        assertEquals("p", task.getInitialParameter());
        assertEquals(MisfirePolicy.SKIP, task.getMisfirePolicy());
    }

    @Test
    public void testNumericFieldsFromStrings() {
        Map<String, Object> record = baseRecord();
        record.put("cron", "0 0 12 * * ?");
        record.put("timeout", "2000");
        record.put("maxRetryCount", "4");
        BeanReflectionTask task = bean(record);
        assertEquals(2000L, task.getTimeout());
        assertEquals(4, task.getMaxRetryCount());
    }

    @Test
    public void testDefaultsWhenAbsent() {
        Map<String, Object> record = baseRecord();
        record.put("cron", "0 0 12 * * ?");
        BeanReflectionTask task = bean(record);
        assertEquals(-1L, task.getTimeout());
        assertEquals(0, task.getMaxRetryCount());
        assertEquals(MisfirePolicy.FIRE_ONCE_NOW, task.getMisfirePolicy());
        assertEquals("execute", task.getTaskMethodName());
    }

    @Test
    public void testExecuteReachesTargetReflectively() {
        Map<String, Object> record = baseRecord();
        record.put("cron", "0 0 12 * * ?");
        ReflectiveTarget.resetCalls();
        Object result = bean(record).execute("arg");
        assertEquals("reflective:arg", result);
        assertTrue(ReflectiveTarget.getCalls() >= 1);
    }

    @Test
    public void testToString() {
        Map<String, Object> record = baseRecord();
        record.put("cron", "0 0 12 * * ?");
        assertTrue(bean(record).toString().contains("g#n"));
    }

    @Test
    public void testNullRecordRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        new DefaultTaskFactory().createBeanReflectionTask(null).getTaskId();
    
        });
    }

}
