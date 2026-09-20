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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import com.github.cronsmith.utils.ExecutorUtils;

/**
 * 
 * Supplies the two pools a scheduler runs on: one thread driving the clock, and a pool the task
 * bodies run on. Keeping them apart is what stops a slow task from delaying the clock.
 * 
 * @Description: ExecutorServiceFactory
 * @Author: Fred Feng
 * @Date: 06/04/2025
 * @Version 1.0.0
 */
public interface ExecutorServiceFactory {

    ScheduledExecutorService getSchedulerThreads();

    ExecutorService getWorkerThreads();

    /**
     * Whether the scheduler owns these pools and should shut them down with itself. False when the
     * pools are shared with the rest of the application.
     */
    default boolean isAutoClosed() {
        return false;
    }

    default void shutdown(ExecutorService executorService) {
        ExecutorUtils.gracefulShutdown(executorService, 60000L);
    }

}
