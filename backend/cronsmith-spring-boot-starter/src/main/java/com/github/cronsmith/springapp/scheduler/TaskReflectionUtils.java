package com.github.cronsmith.springapp.scheduler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.github.cronsmith.utils.MapUtils;

/**
 * 
 * Resolves the classes, instances and methods a stored task definition names.
 * 
 * <p>
 * Everything is cached: a task firing every second would otherwise pay for a class load and a
 * method lookup on every run. The cache also gives a task class a single instance, so a task can
 * keep state across runs; this is the reason task bodies must be thread safe.
 * 
 * @Description: TaskReflectionUtils
 * @Author: Fred Feng
 * @Date: 25/04/2025
 * @Version 1.0.0
 */
public abstract class TaskReflectionUtils {

    private static final Map<String, Class<?>> taskClasses = new ConcurrentHashMap<>();
    private static final Map<String, Object> taskObjects = new ConcurrentHashMap<>();
    private static final Map<String, Method> taskMethods = new ConcurrentHashMap<>();

    private static volatile TaskFactory taskFactory = new DefaultTaskFactory();

    /**
     * Replaces the factory used to rebuild tasks from stored rows.
     */
    public static void setTaskFactory(TaskFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("TaskFactory is required");
        }
        taskFactory = factory;
    }

    public static TaskFactory getTaskFactory() {
        return taskFactory;
    }

    /**
     * The task a stored row stands for. There are two kinds, told apart by the row itself:
     *
     * <ul>
     * <li>A row carrying a request line (a non-blank {@code url}) is an HTTP-API task, built by the
     * factory straight from the row.
     * <li>Everything else is a bean task. A class that implements {@link Task} directly is
     * instantiated as itself; a plain target class (no {@link Task}), or one absent from this node,
     * is wrapped by the factory as a bean-reflection task, which knows how to reach its body — even
     * over the network.
     * </ul>
     */
    public static Task getTaskObject(String taskClassName, Map<String, Object> record) {
        Object url = record != null ? record.get("url") : null;
        if (url != null && !url.toString().isBlank()) {
            return taskFactory.createApiCallTask(record);
        }
        if (taskClassName == null || taskClassName.trim().isEmpty()) {
            return taskFactory.createBeanReflectionTask(record);
        }
        Class<?> taskClass;
        try {
            taskClass = getTaskClass(taskClassName);
        } catch (TaskInvocationException e) {
            // The task's class is not on this node — for instance a distributed executor's class,
            // absent on a server that only triggers the task. Let the factory build a bean-reflection
            // form (which may reach the body over the network).
            return taskFactory.createBeanReflectionTask(record);
        }
        if (!Task.class.isAssignableFrom(taskClass)
                || BeanReflectionTask.class.isAssignableFrom(taskClass)
                || ApiCallTask.class.isAssignableFrom(taskClass)) {
            return taskFactory.createBeanReflectionTask(record);
        }
        return (Task) getTaskObject(taskClassName);
    }

    /**
     * The shared instance of the named class, constructed through its no-argument constructor.
     */
    public static Object getTaskObject(String taskClassName) {
        return MapUtils.getOrCreate(taskObjects, taskClassName,
                () -> doGetTaskObject(taskClassName));
    }

    private static Object doGetTaskObject(String taskClassName) {
        Class<?> taskClass = getTaskClass(taskClassName);
        try {
            java.lang.reflect.Constructor<?> constructor = taskClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new TaskInvocationException(
                    "Cannot instantiate task class: " + taskClassName + ". A public no-argument"
                            + " constructor is required.",
                    e);
        }
    }

    /**
     * The method to call for a task, cached per class and method name.
     */
    public static Method getTaskMethod(TaskId taskId, String taskClassName, String taskMethodName) {
        String key = taskClassName + "#" + taskMethodName;
        return MapUtils.getOrCreate(taskMethods, key,
                () -> doGetTaskMethod(taskClassName, taskMethodName));
    }

    private static Method doGetTaskMethod(String taskClassName, String taskMethodName) {
        Class<?> taskClass = getTaskClass(taskClassName);
        try {
            Method method = taskClass.getMethod(taskMethodName, String.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            // Fall through to the wider search below: the method may be declared on the class
            // itself without being public, or take a parameter a String can be assigned to.
        }
        for (Class<?> current = taskClass; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(taskMethodName) || method.getParameterCount() != 1
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (method.getParameterTypes()[0].isAssignableFrom(String.class)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new TaskInvocationException("No method '" + taskMethodName
                + "(String)' on task class: " + taskClassName);
    }

    public static Class<?> getTaskClass(String taskClassName) {
        return MapUtils.getOrCreate(taskClasses, taskClassName,
                () -> doGetTaskClass(taskClassName));
    }

    private static Class<?> doGetTaskClass(String taskClassName) {
        if (taskClassName == null || taskClassName.trim().isEmpty()) {
            throw new TaskInvocationException("Task class name is required");
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = TaskReflectionUtils.class.getClassLoader();
        }
        try {
            return Class.forName(taskClassName, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new TaskInvocationException("No such task class: " + taskClassName, e);
        }
    }

    /**
     * The class name a task is filtered and stored by: for a custom task that is the class it
     * stands in for, rather than the wrapper's own class.
     */
    public static String taskClassNameOf(Task task) {
        if (task instanceof BeanReflectionTask) {
            return ((BeanReflectionTask) task).getTaskClassName();
        }
        if (task instanceof ApiCallTask) {
            // A data-only HTTP task stands for no class of its own; nothing to filter it by.
            return "";
        }
        return task.getClass().getName();
    }

    /**
     * Empties every cache. Intended for tests, and for hosts that reload classes.
     */
    public static void clearCaches() {
        taskClasses.clear();
        taskObjects.clear();
        taskMethods.clear();
    }

}
