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

import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskInvocationException;
import com.github.cronsmith.springapp.scheduler.TaskReflectionUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 *
 * @Description: DefaultTaskFactory
 * @Author: Fred Feng
 * @Date: 08/04/2025
 * @Version 1.0.0
 */
public class DefaultTaskFactory implements TaskFactory {

    @Override
    public Task createBeanReflectionTask(Map<String, Object> record) {
        return new DefaultBeanReflectionTask(record);
    }

    @Override
    public Task createApiCallTask(Map<String, Object> record) {
        return new DefaultApiCallTask(record);
    }

    static class DefaultBeanReflectionTask extends BeanReflectionTask {

        DefaultBeanReflectionTask(Map<String, Object> record) {
            super(record);
        }

        @Override
        protected Object invokeTaskMethod(TaskId taskId, String taskClassName,
                                          String taskMethodName, String initialParameter) {
            Object taskObject = TaskReflectionUtils.getTaskObject(taskClassName);
            Method method =
                    TaskReflectionUtils.getTaskMethod(taskId, taskClassName, taskMethodName);
            try {
                return method.invoke(taskObject, initialParameter);
            } catch (InvocationTargetException e) {
                throw new TaskInvocationException(e.getTargetException().getMessage(),
                        e.getTargetException());
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new TaskInvocationException(e.getMessage(), e);
            }
        }
    }

    static class DefaultApiCallTask extends ApiCallTask {

        DefaultApiCallTask(Map<String, Object> record) {
            super(record);
        }

        @Override
        protected Object sendHttpRequest(TaskId taskId, String url, String httpMethodName, Map<String, String> httpHeaders,
                                         String dataType, String initialParameter) throws IOException {
            return HttpClientUtils.sendRequest(url, httpMethodName, httpHeaders, dataType, initialParameter);
        }
    }
}
