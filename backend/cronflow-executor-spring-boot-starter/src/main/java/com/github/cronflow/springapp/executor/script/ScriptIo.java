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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * The script input/output contract: how a {@link ScriptSpec} becomes {@code input.json} and environment
 * variables, and how captured streams and {@code result.json} are read back. Runners (local and docker)
 * share this so a script sees the same contract regardless of where it runs.
 
 *
 * @Description: ScriptIo
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
final class ScriptIo {

    private ScriptIo() {}

    /** Build the {@code /script/input.json} object: {@code {context, params, channels}}. */
    static Map<String, Object> inputJson(ScriptSpec spec) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("runId", spec.runId());
        context.put("name", spec.name());
        context.put("attempt", spec.attempt());
        context.put("deadlineMs", spec.timeoutMillis());

        Map<String, Object> params = new LinkedHashMap<>();
        if (spec.param() != null) {
            params.put("param", spec.param());
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("context", context);
        root.put("params", params);
        root.put("channels", spec.channels() == null ? Map.of() : spec.channels());
        return root;
    }

    /** Convenience environment mirror of the run context. Secrets are never put here. */
    static Map<String, String> env(ScriptSpec spec, String scriptDir) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("SCRIPT_DIR", scriptDir);
        env.put("SCRIPT_RUN_ID", nullToEmpty(spec.runId()));
        env.put("SCRIPT_NAME", nullToEmpty(spec.name()));
        env.put("SCRIPT_ATTEMPT", Integer.toString(spec.attempt()));
        env.put("SCRIPT_DEADLINE_MS", Long.toString(spec.timeoutMillis()));
        return env;
    }

    /** Read a captured stream file, truncating to {@code maxBytes} (keeping the tail note). */
    static String readCapped(Path file, int maxBytes) {
        try {
            if (!Files.exists(file)) {
                return "";
            }
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length <= maxBytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            String head = new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
            return head + "\n...[truncated " + (bytes.length - maxBytes) + " bytes]";
        } catch (IOException e) {
            return "";
        }
    }

    /** Parse {@code result.json} into a map, or null when absent or unparseable. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> readResultJson(Path file, ObjectMapper mapper) {
        try {
            if (!Files.exists(file)) {
                return null;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return null;
            }
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
