package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.BeanReflectionTask;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskInvocationException;
import com.github.cronsmith.springapp.scheduler.TaskReflectionUtils;
import com.github.cronsmith.springapp.scheduler.test.TestTasks.ReflectiveTarget;

/**
 * 
 * Covers how a stored task definition is resolved back into a class, an instance and a method,
 * including the caching that keeps a per-second task from paying reflection costs on every run.
 * 
 * @Description: TaskReflectionUtilsTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TaskReflectionUtilsTests {

    private static final String TARGET = ReflectiveTarget.class.getName();

    @BeforeEach
    public void setUp() {
        TaskReflectionUtils.clearCaches();
        ReflectiveTarget.resetCalls();
    }

    @Test
    public void testGetTaskClass() {
        assertEquals(ReflectiveTarget.class, TaskReflectionUtils.getTaskClass(TARGET));
    }

    @Test
    public void testTaskClassIsCached() {
        assertSame(TaskReflectionUtils.getTaskClass(TARGET),
                TaskReflectionUtils.getTaskClass(TARGET));
    }

    @Test
    public void testMissingClassThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(TaskInvocationException.class, () -> {
        TaskReflectionUtils.getTaskClass("com.example.NoSuchClass");
    
        });
    }

    @Test
    public void testBlankClassNameThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(TaskInvocationException.class, () -> {
        TaskReflectionUtils.getTaskClass("  ");
    
        });
    }

    @Test
    public void testTaskObjectIsSingletonPerClass() {
        Object one = TaskReflectionUtils.getTaskObject(TARGET);
        Object two = TaskReflectionUtils.getTaskObject(TARGET);
        assertSame(one, two, "a task class gets one shared instance");
    }

    @Test
    public void testInvokeResolvedMethod() throws Exception {
        Object target = TaskReflectionUtils.getTaskObject(TARGET);
        Method method = TaskReflectionUtils.getTaskMethod(TaskId.of("t"), TARGET, "execute");
        assertEquals("reflective:hi", method.invoke(target, "hi"));
        assertEquals(1, ReflectiveTarget.getCalls());
    }

    @Test
    public void testResolveAlternativeMethodName() throws Exception {
        Object target = TaskReflectionUtils.getTaskObject(TARGET);
        Method method = TaskReflectionUtils.getTaskMethod(TaskId.of("t"), TARGET, "other");
        assertEquals("other:x", method.invoke(target, "x"));
    }

    @Test
    public void testResolvePackagePrivateMethod() throws Exception {
        Object target = TaskReflectionUtils.getTaskObject(TARGET);
        Method method =
                TaskReflectionUtils.getTaskMethod(TaskId.of("t"), TARGET, "packagePrivate");
        assertEquals("package:y", method.invoke(target, "y"));
    }

    @Test
    public void testMissingMethodThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(TaskInvocationException.class, () -> {
        TaskReflectionUtils.getTaskMethod(TaskId.of("t"), TARGET, "noSuchMethod");
    
        });
    }

    @Test
    public void testTaskClassNameOfPlainTask() {
        Task plain = new Task() {
            @Override
            public com.github.cronsmith.cron.CronExpression getCronExpression() {
                return null;
            }

            @Override
            public Object execute(String p) {
                return null;
            }
        };
        assertTrue(TaskReflectionUtils.taskClassNameOf(plain).contains("TaskReflectionUtilsTests"));
    }

    @Test
    public void testTaskClassNameOfCustomTaskUsesTargetClass() {
        Map<String, Object> record = new HashMap<>();
        record.put("taskGroup", "g");
        record.put("taskName", "n");
        record.put("taskClass", TARGET);
        record.put("cron", "0 0 12 * * ?");
        BeanReflectionTask custom =
                (BeanReflectionTask) TaskReflectionUtils.getTaskFactory()
                        .createBeanReflectionTask(record);
        assertEquals(TARGET, TaskReflectionUtils.taskClassNameOf(custom));
    }

    @Test
    public void testGetTaskObjectForCustomClassWrapsInCustomTask() {
        Map<String, Object> record = new HashMap<>();
        record.put("taskGroup", "g");
        record.put("taskName", "n");
        record.put("taskClass", TARGET);
        record.put("cron", "0 0 12 * * ?");
        // ReflectiveTarget is not a Task, so it is wrapped by the custom task factory rather than
        // used as a task itself.
        Task task = TaskReflectionUtils.getTaskObject(TARGET, record);
        assertNotNull(task);
        assertTrue(task instanceof BeanReflectionTask);
        assertEquals(TaskId.of("g", "n"), task.getTaskId());
    }

}
