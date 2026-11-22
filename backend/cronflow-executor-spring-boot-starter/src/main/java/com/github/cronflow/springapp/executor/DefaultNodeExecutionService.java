/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cronflow.springapp.executor;

import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;
import com.github.cronflow.springapp.executor.script.ScriptLanguage;
import com.github.cronflow.springapp.executor.script.ScriptResult;
import com.github.cronflow.springapp.executor.script.ScriptRunner;
import com.github.cronflow.springapp.executor.script.ScriptSpec;
import com.github.cronflow.springapp.executor.pojo.NodeRunRequest;
import com.github.cronflow.springapp.executor.pojo.NodeRunResult;

/**
 * Default {@link NodeExecutionService}: the "HTTP → spring bean + method" invocation, the same shape
 * the scheduler uses for a task, but for a DAG node — the method reads a {@link DagState} and returns
 * the channels it changed.
 *
 * <p>
 * The bean is resolved by name; the method is matched by name, preferring a {@code (GraphState)}
 * parameter and falling back to a no-arg form. A {@code Map} return is taken as the channel updates;
 * {@code null} / {@code void} means the node changed nothing.
 *
 * @Description: DefaultNodeExecutionService
 * @Author: Fred Feng
 * @Date: 14/09/2026
 * @Version 1.0.0
 */
public class DefaultNodeExecutionService implements NodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNodeExecutionService.class);

    private final ApplicationContext applicationContext;

    /** Optional script script runner. Null unless {@code cronflow.script.enabled=true} wired one. */
    private ScriptRunner scriptRunner;

    public DefaultNodeExecutionService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** Injected by the script auto-configuration when the feature is on. */
    @Autowired(required = false)
    public void setScriptRunner(ScriptRunner scriptRunner) {
        this.scriptRunner = scriptRunner;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeRunResult run(NodeRunRequest request) {
        if (request.isScript()) {
            return runScriptNode(request);
        }
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

    private NodeRunResult runScriptNode(NodeRunRequest request) {
        if (scriptRunner == null) {
            return NodeRunResult.failure(
                    "script is not enabled on this executor (cronflow.script.enabled=false)");
        }
        ScriptLanguage lang = ScriptLanguage.from(request.language());
        if (lang == null || !scriptRunner.supports(lang)) {
            return NodeRunResult.failure("script language not supported here: " + request.language());
        }
        String id = request.graph() + "/" + request.node();
        ScriptResult res = scriptRunner.run(
                ScriptSpec.node(lang, request.script(), id, id, request.state(), 0L, request.datasource()));
        if (res.success()) {
            log.info("cronflow: ran script node {} of graph {} ({}) here", request.node(), request.graph(),
                    lang);
            return NodeRunResult.success(res.channelUpdates());
        }
        return NodeRunResult.failure(res.failureDetail());
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
