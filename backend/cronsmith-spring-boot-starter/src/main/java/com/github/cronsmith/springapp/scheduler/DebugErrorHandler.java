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

import java.io.PrintStream;
import java.time.LocalDateTime;

/**
 * 
 * Prints failures straight to a stream. Meant for tests and for getting a stack trace out of a
 * process that has no logging configured; use {@link LoggingErrorHandler} in production.
 * 
 * @Description: DebugErrorHandler
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public class DebugErrorHandler implements ErrorHandler {

    private final PrintStream out;

    public DebugErrorHandler() {
        this(System.err);
    }

    public DebugErrorHandler(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onHandleScheduler(Throwable e) {
        print("scheduler", null, e);
    }

    @Override
    public void onHandleTask(LocalDateTime datetime, Throwable e) {
        print("task", datetime, e);
    }

    @Override
    public void onHandleTaskResult(LocalDateTime datetime, Throwable e) {
        print("task result", datetime, e);
    }

    private void print(String what, LocalDateTime datetime, Throwable e) {
        out.println("[cronsmith] " + what + " failed"
                + (datetime != null ? " at " + datetime : "") + ": " + e.getMessage());
        e.printStackTrace(out);
    }

}
