package com.github.cronflow.springapp.executor;

import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

/**
 * Default {@link NodeExecutionService}: the "HTTP → spring bean + method" invocation, the same shape
 * cronsmith uses for a task, but for a DAG node — the method reads a {@link DagState} and returns
 * the channels it changed.
 *
 * <p>
 * The bean is resolved by name; the method is matched by name, preferring a {@code (GraphState)}
 * parameter and falling back to a no-arg form. A {@code Map} return is taken as the channel updates;
 * {@code null} / {@code void} means the node changed nothing.
 *
 * @Description: DefaultNodeExecutionService
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class DefaultNodeExecutionService implements NodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNodeExecutionService.class);

    private final ApplicationContext applicationContext;

    public DefaultNodeExecutionService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeRunResult run(NodeRunRequest request) {
        try {
            Object bean = applicationContext.getBean(request.beanName());
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            DagState state = DagState.of(request.state() == null ? Map.of() : request.state());
            Method method = resolveMethod(targetClass, request.methodName());
            if (method == null) {
                return NodeRunResult.failure("No node method '" + request.methodName() + "' on bean '"
                        + request.beanName() + "'");
            }
            ReflectionUtils.makeAccessible(method);
            Object result = method.getParameterCount() == 1 ? method.invoke(bean, state)
                    : method.invoke(bean);
            Map<String, Object> updates = result instanceof Map ? (Map<String, Object>) result : Map.of();
            log.info("cronflow: ran node {} of graph {} ({}#{}) here", request.node(), request.graph(),
                    request.beanName(), request.methodName());
            return NodeRunResult.success(updates);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            log.warn("Graph {} node {} ({}#{}) failed: {}", request.graph(), request.node(),
                    request.beanName(), request.methodName(), cause.toString());
            return NodeRunResult.failure(cause.toString());
        }
    }

    /** Prefer a method taking a single {@link DagState}, else a no-arg method of the same name. */
    private Method resolveMethod(Class<?> targetClass, String methodName) {
        Method noArg = null;
        for (Method m : ReflectionUtils.getAllDeclaredMethods(targetClass)) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == DagState.class) {
                return m;
            }
            if (m.getParameterCount() == 0 && noArg == null) {
                noArg = m;
            }
        }
        return noArg;
    }

}
