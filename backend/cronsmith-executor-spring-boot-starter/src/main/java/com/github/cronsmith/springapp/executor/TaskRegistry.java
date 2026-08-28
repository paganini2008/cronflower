package com.github.cronsmith.springapp.executor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Scans every bean for {@link Task}-annotated methods and turns them into {@link TaskMetadata}.
 *
 * @Description: TaskRegistry
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class TaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(TaskRegistry.class);

    private final ApplicationContext applicationContext;

    public TaskRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Collect all task definitions in this application. {@code applicationName} is the default group
     * for tasks that do not name one.
     */
    public List<TaskMetadata> scan(String applicationName) {
        List<TaskMetadata> tasks = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (RuntimeException e) {
                // Lazy/abstract/scoped beans that cannot be resolved here simply carry no tasks.
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            ReflectionUtils.doWithMethods(targetClass, method -> {
                Task annotation = AnnotatedElementUtils.findMergedAnnotation(method, Task.class);
                if (annotation == null) {
                    return;
                }
                validateSignature(method);
                String group = StringUtils.hasText(annotation.group()) ? annotation.group()
                        : applicationName;
                String name = StringUtils.hasText(annotation.name()) ? annotation.name()
                        : beanName + "." + method.getName();
                tasks.add(new TaskMetadata(group, name, targetClass.getName(), beanName,
                        method.getName(), resolveSchedule(annotation, method),
                        resolveParser(annotation, method), annotation.description(),
                        annotation.initialParameter(), annotation.timeout(),
                        annotation.maxRetryCount(), annotation.retryInterval(),
                        annotation.misfirePolicy()));
                log.debug("Discovered @Task {}#{} on {}.{}", group, name, targetClass.getSimpleName(),
                        method.getName());
            });
        }
        return tasks;
    }

    /**
     * The single schedule the task is defined by: its cron, or a fixed interval turned into an
     * ISO-8601 duration string that the server reads as a periodic schedule. Exactly one of
     * {@code cron}, {@code interval} and {@code iso} must be set.
     */
    private String resolveSchedule(Task annotation, Method method) {
        boolean hasCron = StringUtils.hasText(annotation.cron());
        boolean hasInterval = annotation.interval() > 0;
        boolean hasIso = StringUtils.hasText(annotation.iso());
        int count = (hasCron ? 1 : 0) + (hasInterval ? 1 : 0) + (hasIso ? 1 : 0);
        if (count != 1) {
            throw new IllegalStateException("@Task " + method
                    + " must set exactly one of cron / interval / iso");
        }
        if (hasCron) {
            return annotation.cron().trim();
        }
        if (hasIso) {
            return annotation.iso().trim();
        }
        return java.time.Duration
                .ofMillis(annotation.intervalUnit().toMillis(annotation.interval())).toString();
    }

    /**
     * Which syntax the {@code cron} expression is in: {@code "cron"} (traditional) or {@code "ycron"}
     * (year-based). Validated here so a typo fails fast at startup rather than on the server.
     */
    private String resolveParser(Task annotation, Method method) {
        String parser = annotation.parser().trim().toLowerCase();
        if (!parser.equals("cron") && !parser.equals("ycron")) {
            throw new IllegalStateException("@Task " + method + " has an unknown parser '"
                    + annotation.parser() + "'; use \"cron\" or \"ycron\"");
        }
        return parser;
    }

    private void validateSignature(Method method) {
        int count = method.getParameterCount();
        boolean ok = count == 0 || (count == 1 && method.getParameterTypes()[0] == String.class);
        if (!ok) {
            throw new IllegalStateException("@Task method " + method
                    + " must take either no arguments or a single String argument");
        }
    }

}
