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
package com.github.cronsmith.springapp.scheduler;

import com.github.cronsmith.springapp.scheduler.TaskId;
import java.util.Map;

/**
 *
 * A task whose body is a method on another class, reached reflectively. What identifies the work —
 * the class name and the method name — is stored as data, so the task can be rebuilt from a database
 * row after a restart.
 *
 * @Description: BeanReflectionTask
 * @Author: Fred Feng
 * @Date: 08/04/2025
 * @Version 1.0.0
 */
public abstract class BeanReflectionTask extends AbstractTask {

    public BeanReflectionTask(Map<String, Object> record) {
        super(record);
    }

    public String getTaskClassName() {
        return stringOf("taskClass", null);
    }

    public String getTaskMethodName() {
        return stringOf("taskMethod", DEFAULT_METHOD_NAME);
    }

    /** The Spring bean the method lives on, when the task is dispatched to one; null otherwise. */
    public String getBeanName() {
        return stringOf("beanName", null);
    }

    /** The executor application a remote dispatch targets; null for a local reflective task. */
    public String getApplication() {
        return stringOf("application", null);
    }

    @Override
    public Object execute(String initialParameter) {
        return invokeTaskMethod(getTaskId(), getTaskClassName(), getTaskMethodName(),
                initialParameter);
    }

    protected abstract Object invokeTaskMethod(TaskId taskId, String taskClassName,
            String taskMethodName, String initialParameter);
}
