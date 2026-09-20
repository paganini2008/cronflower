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
 * Thrown when a task could not be invoked, or when the task body itself failed. The cause is always
 * what the task actually threw, never a reflection wrapper.
 * 
 * @Description: TaskInvocationException
 * @Author: Fred Feng
 * @Date: 20/04/2025
 * @Version 1.0.0
 */
public class TaskInvocationException extends TaskException {

    private static final long serialVersionUID = 330511858530035307L;

    public TaskInvocationException(String msg) {
        super(msg);
    }

    public TaskInvocationException(String msg, Throwable e) {
        super(msg, e);
    }

}
