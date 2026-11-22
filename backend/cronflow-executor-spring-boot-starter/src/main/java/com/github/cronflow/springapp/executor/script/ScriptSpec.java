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

import java.util.Map;

/**
 * One script run request handed to a {@link ScriptRunner}. The runner materializes the source and a
 * {@code /script/input.json} built from these fields, runs it, and returns a {@link ScriptResult}.
 *
 * @param language the script language
 * @param source the script text
 * @param runId correlation id for logs and the input context
 * @param name task {@code group/name} or DAG {@code graph/node}, for the input context
 * @param attempt retry attempt number, 0-based
 * @param timeoutMillis wall-clock budget, the runner kills the run past this, 0 means a runner default
 * @param param the task parameter (null for a DAG node)
 * @param channels the inbound DAG channel state (null for a plain task)
 * @param datasource the named datasource for a SQL script (null otherwise)
  *
 * @Description: ScriptSpec
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
public record ScriptSpec(ScriptLanguage language, String source, String runId, String name, int attempt,
        long timeoutMillis, String param, Map<String, Object> channels, String datasource) {

    /** A plain task script: a parameter, no channels. */
    public static ScriptSpec task(ScriptLanguage language, String source, String runId, String name,
            int attempt, long timeoutMillis, String param, String datasource) {
        return new ScriptSpec(language, source, runId, name, attempt, timeoutMillis, param, null,
                datasource);
    }

    /** A DAG node script: inbound channel state, no task parameter. */
    public static ScriptSpec node(ScriptLanguage language, String source, String runId, String name,
            Map<String, Object> channels, long timeoutMillis, String datasource) {
        return new ScriptSpec(language, source, runId, name, 0, timeoutMillis, null, channels,
                datasource);
    }
}
