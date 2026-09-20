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
import java.util.Map;

/**
 *
 * Turns a stored row back into a runnable task. There are exactly two kinds: one whose body is a
 * method reached reflectively, and one whose body is an HTTP request. Replace the default to control
 * how each kind finds its body — for instance by resolving a bean from a dependency-injection
 * container, or by dispatching the call to a remote executor.
 *
 * @Description: TaskFactory
 * @Author: Fred Feng
 * @Date: 08/04/2025
 * @Version 1.0.0
 */
public interface TaskFactory {

    Task createBeanReflectionTask(Map<String, Object> record);

    Task createApiCallTask(Map<String, Object> record);

}
