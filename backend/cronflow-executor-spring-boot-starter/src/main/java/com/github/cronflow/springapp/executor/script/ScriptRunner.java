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
package com.github.cronflow.springapp.executor.script;

/**
 * Executes one script script in isolation and returns its captured result. The default implementation
 * runs OS-process languages (shell, python) either on the host ({@code LocalScriptRunner}) or in a
 * per-run container ({@code DockerScriptRunner}), and runs SQL over JDBC. Which backend is active is a
 * per-executor choice ({@code cronflow.script.runner}). The whole feature is off unless
 * {@code cronflow.script.enabled=true}.
  *
 * @Description: ScriptRunner
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
public interface ScriptRunner {

    /** Run the script to completion (bounded by {@link ScriptSpec#timeoutMillis()}) and return its result. */
    ScriptResult run(ScriptSpec spec);

    /** Whether this runner can execute the given language in the current configuration. */
    boolean supports(ScriptLanguage language);
}
