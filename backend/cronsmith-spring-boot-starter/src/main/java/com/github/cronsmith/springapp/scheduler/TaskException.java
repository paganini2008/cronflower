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
 * Base class for every failure raised by the task extension.
 * 
 * @Description: TaskException
 * @Author: Fred Feng
 * @Date: 16/04/2025
 * @Version 1.0.0
 */
public class TaskException extends RuntimeException {

    private static final long serialVersionUID = -8660771248670135685L;

    public TaskException() {
        super();
    }

    public TaskException(String msg) {
        super(msg);
    }

    public TaskException(String msg, Throwable e) {
        super(msg, e);
    }

}
