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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs shell and python scripts as a host OS process, and delegates SQL to a {@link SqlScriptExecutor}.
 * The script source and a {@code input.json} are materialized into a per-run temp dir mounted (by
 * convention) at {@code /script}; stdout, stderr, exit code and an optional {@code result.json} are
 * captured. This runner gives timeout, temp-dir and output-size bounds, but shares the host: it is the
 * {@code cronflow.script.runner=local} option for executors without Docker. Prefer {@code DockerScriptRunner}
 * for untrusted code.
  *
 * @Description: LocalScriptRunner
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
public class LocalScriptRunner implements ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalScriptRunner.class);

    private final CronflowScriptProperties props;
    private final ObjectMapper mapper;
    private final SqlScriptExecutor sqlExecutor;

    public LocalScriptRunner(CronflowScriptProperties props, ObjectMapper mapper,
            SqlScriptExecutor sqlExecutor) {
        this.props = props;
        this.mapper = mapper;
        this.sqlExecutor = sqlExecutor;
    }

    @Override
    public boolean supports(ScriptLanguage language) {
        if (!ScriptLanguages.allowed(props, language)) {
            return false;
        }
        return switch (language) {
            case SHELL, PYTHON, JAVA -> true; // JAVA runs as a single-file source program (java X.java)
            case SQL -> sqlExecutor != null && sqlExecutor.hasDatasource();
        };
    }

    @Override
    public ScriptResult run(ScriptSpec spec) {
        if (spec.language() == ScriptLanguage.SQL) {
            if (sqlExecutor == null) {
                return ScriptResults.error("SQL requested but no datasource is configured");
            }
            return sqlExecutor.run(spec);
        }
        long start = System.currentTimeMillis();
        Path dir = null;
        try {
            dir = createWorkDir(spec);
            Path script = dir.resolve("script." + spec.language().fileExtension());
            Files.writeString(script, spec.source() == null ? "" : spec.source(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("input.json"),
                    mapper.writeValueAsString(ScriptIo.inputJson(spec)), StandardCharsets.UTF_8);
            Path out = dir.resolve("stdout.log");
            Path err = dir.resolve("stderr.log");

            ProcessBuilder pb = new ProcessBuilder(command(spec.language(), script));
            pb.directory(dir.toFile());
            pb.redirectOutput(out.toFile());
            pb.redirectError(err.toFile());
            pb.environment().putAll(ScriptIo.env(spec, dir.toString()));

            long timeout = spec.timeoutMillis() > 0 ? spec.timeoutMillis() : props.getDefaultTimeoutMillis();
            Process p = pb.start();
            boolean finished = p.waitFor(timeout, TimeUnit.MILLISECONDS);
            boolean timedOut = false;
            int exit;
            if (!finished) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
                timedOut = true;
                exit = -1;
            } else {
                exit = p.exitValue();
            }
            String stdout = ScriptIo.readCapped(out, props.getMaxOutputBytes());
            String stderr = ScriptIo.readCapped(err, props.getMaxOutputBytes());
            Map<String, Object> result = ScriptIo.readResultJson(dir.resolve("result.json"), mapper);
            long dur = System.currentTimeMillis() - start;
            boolean success = !timedOut && exit == 0;
            return new ScriptResult(success, exit, timedOut, stdout, stderr, result, dur, null);
        } catch (Exception e) {
            log.warn("local script run failed for {}", spec.name(), e);
            return new ScriptResult(false, -1, false, "", "", null,
                    System.currentTimeMillis() - start, e.toString());
        } finally {
            deleteQuietly(dir);
        }
    }

    private List<String> command(ScriptLanguage lang, Path script) {
        List<String> cmd = new ArrayList<>();
        switch (lang) {
            case SHELL -> cmd.add(props.getShellBin());
            case JAVA -> cmd.add(props.getJavaBin()); // java X.java — single-file source launch (JDK 11+)
            default -> cmd.add(props.getPythonBin());
        }
        cmd.add(script.toString());
        return cmd;
    }

    private Path createWorkDir(ScriptSpec spec) throws IOException {
        String base = props.getWorkDir();
        Path parent = base == null || base.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"))
                : Path.of(base);
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent, "script-");
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
