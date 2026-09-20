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

import java.time.LocalDateTime;

/**
 * 
 * Where failures go that have nowhere else to be reported: a broken scheduler tick, a task body
 * that threw, a result callback or listener that threw. Implementations must not throw themselves.
 * 
 * @Description: ErrorHandler
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public interface ErrorHandler {

    /** The clock tick itself failed. The scheduler keeps running. */
    default void onHandleScheduler(Throwable e) {}

    /** A task body threw. */
    default void onHandleTask(LocalDateTime datetime, Throwable e) {}

    /** A result callback or a listener threw. */
    default void onHandleTaskResult(LocalDateTime datetime, Throwable e) {}

}
