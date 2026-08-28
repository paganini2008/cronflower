package com.github.cronsmith.springapp.executor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default {@link TaskExecutionService}: reflectively invokes the target bean method on a pool
 * thread, then reports the outcome back to the server.
 *
 * <p>
 * Execution is asynchronous: {@code /cronsmith/run} returns immediately and the actual work happens
 * on a pool thread. When it finishes — success or failure — a {@link CompleteRequest} is sent to the
 * server, which owns retry, timeout and logging. This executor never retries on its own.
 *
 * @Description: DefaultTaskExecutionService
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class DefaultTaskExecutionService implements TaskExecutionService, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskExecutionService.class);

    private final ApplicationContext applicationContext;
    private final CronsmithServerClient serverClient;
    private final ExecutorIdentity identity;
    private final ExecutorService pool;

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParserContext templateContext = new TemplateParserContext();
    private final BeanFactoryResolver beanFactoryResolver;

    public DefaultTaskExecutionService(ApplicationContext applicationContext,
            CronsmithServerClient serverClient, ExecutorIdentity identity, int poolSize) {
        this.applicationContext = applicationContext;
        this.serverClient = serverClient;
        this.identity = identity;
        this.beanFactoryResolver = new BeanFactoryResolver(applicationContext);
        final AtomicInteger seq = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(Math.max(1, poolSize), r -> {
            Thread t = new Thread(r, "cronsmith-invoker-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void dispatch(RunRequest request) {
        long firedAt = System.currentTimeMillis();
        pool.execute(() -> run(request, firedAt));
    }

    private void run(RunRequest request, long firedAt) {
        boolean success = false;
        String returnValue = null;
        String errorDetail = null;
        try {
            Object bean = resolveBean(request);
            Method method = resolveMethod(bean, request.methodName());
            ReflectionUtils.makeAccessible(method);
            Object result;
            if (method.getParameterCount() == 1) {
                result = method.invoke(bean, resolveParameter(request.initialParameter()));
            } else {
                result = method.invoke(bean);
            }
            returnValue = result == null ? null : String.valueOf(result);
            success = true;
        } catch (InvocationTargetException e) {
            errorDetail = stackTrace(e.getTargetException());
            log.warn("Task {} threw", request.taskName(), e.getTargetException());
        } catch (Exception e) {
            errorDetail = stackTrace(e);
            log.warn("Task {} could not be invoked", request.taskName(), e);
        }
        long completedAt = System.currentTimeMillis();
        serverClient.complete(new CompleteRequest(request.executionId(), request.taskGroup(),
                request.taskName(), success, returnValue, errorDetail, firedAt, completedAt,
                completedAt - firedAt, request.attempt(), identity.repr()));
    }

    private Object resolveBean(RunRequest request) throws ClassNotFoundException {
        if (StringUtils.hasText(request.beanName())
                && applicationContext.containsBean(request.beanName())) {
            return applicationContext.getBean(request.beanName());
        }
        Class<?> clazz = ClassUtils.forName(request.className(), applicationContext.getClassLoader());
        return applicationContext.getBean(clazz);
    }

    private Method resolveMethod(Object bean, String methodName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Method method = ReflectionUtils.findMethod(targetClass, methodName, String.class);
        if (method == null) {
            method = ReflectionUtils.findMethod(targetClass, methodName);
        }
        if (method == null) {
            throw new IllegalStateException("No method '" + methodName + "(String)' or '" + methodName
                    + "()' on " + targetClass.getName());
        }
        return method;
    }

    /**
     * A plain constant is returned as is; a {@code #{...}} template is evaluated here, so it can read
     * beans and compute a value fresh on every run.
     */
    private String resolveParameter(String raw) {
        if (raw == null || raw.isEmpty() || !raw.contains(templateContext.getExpressionPrefix())) {
            return raw;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(beanFactoryResolver);
        Object value = expressionParser.parseExpression(raw, templateContext).getValue(context);
        return value == null ? null : String.valueOf(value);
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Override
    public void destroy() {
        pool.shutdownNow();
    }

}
