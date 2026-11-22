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
 * The outcome of a {@link ScriptRunner} run. {@code exitCode == 0} and not {@link #timedOut} means
 * success. {@link #result} is the parsed {@code /script/result.json} (null when the script wrote none):
 * for a task read {@code result.get("result")}, for a DAG node read {@code result.get("channels")}.
 *
 * @param success exit 0 and not timed out
 * @param exitCode process exit code, or a sentinel for a SQL or runner failure
 * @param timedOut whether the wall-clock budget was exceeded and the run was killed
 * @param stdout captured standard output (size-capped by the runner)
 * @param stderr captured standard error (size-capped by the runner)
 * @param result parsed result.json, or null
 * @param durationMillis wall-clock duration
 * @param error a runner-level failure detail (could not start, image missing, timeout), or null
  *
 * @Description: ScriptResult
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
public record ScriptResult(boolean success, int exitCode, boolean timedOut, String stdout,
        String stderr, Map<String, Object> result, long durationMillis, String error) {

    /** The task-style return value: {@code result.result} if present, else the last non-blank stdout line. */
    public String resultValue() {
        if (result != null && result.get("result") != null) {
            return String.valueOf(result.get("result"));
        }
        if (stdout == null || stdout.isBlank()) {
            return null;
        }
        String[] lines = stdout.strip().split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) {
                return lines[i].strip();
            }
        }
        return null;
    }

    /** The DAG channel updates: {@code result.channels} if present, else an empty map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> channelUpdates() {
        if (result != null && result.get("channels") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    /** A short failure summary for the execution log, combining the runner error and stderr tail. */
    public String failureDetail() {
        StringBuilder sb = new StringBuilder();
        if (timedOut) {
            sb.append("timed out after ").append(durationMillis).append("ms; ");
        }
        if (error != null && !error.isBlank()) {
            sb.append(error).append("; ");
        }
        sb.append("exit=").append(exitCode);
        if (stderr != null && !stderr.isBlank()) {
            String s = stderr.strip();
            sb.append("; stderr: ").append(s.length() > 2000 ? s.substring(s.length() - 2000) : s);
        }
        return sb.toString();
    }
}
