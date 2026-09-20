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

/**
 * 
 * Thrown when a task is looked up by an id the task manager does not know.
 * 
 * @Description: TaskDetailNotFoundException
 * @Author: Fred Feng
 * @Date: 01/06/2025
 * @Version 1.0.0
 */
public class TaskDetailNotFoundException extends TaskException {

    private static final long serialVersionUID = -5201635186837107874L;

    public TaskDetailNotFoundException() {
        super();
    }

    public TaskDetailNotFoundException(TaskId taskId) {
        super("No such task: " + taskId);
    }

    public TaskDetailNotFoundException(String msg) {
        super(msg);
    }

}
