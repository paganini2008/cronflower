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

import java.util.Map;

/**
 * 
 * The seam between a stored task definition and the code that actually runs it. Implemented by
 * anything that hosts tasks on behalf of a framework, where the object to call has to come from
 * that framework rather than from a bare constructor.
 * 
 * @Description: TaskInvocation
 * @Author: Fred Feng
 * @Date: 13/04/2025
 * @Version 1.0.0
 */
public interface TaskInvocation {

    /**
     * Resolves the task a stored row stands for.
     */
    Task retrieveTaskObject(String taskClassName, Map<String, Object> record);

    /**
     * Runs the stored task's body and returns what it produced.
     */
    Object invokeTaskMethod(TaskId taskId, String taskClassName, String taskMethodName,
            String initialParameter);

}
