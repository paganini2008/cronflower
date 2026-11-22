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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs shell and python scripts in a fresh per-run container (Docker-first), and delegates SQL to the
 * shared {@link SqlScriptExecutor}. The per-run temp dir is bind-mounted at {@code /script}; the container
 * runs non-root, with cpu, memory and pids limits, a configurable network (default none) and an optional
 * hardened container runtime. One container per run, logs and exit captured, nothing shared between runs.
 * The executor needs access to a Docker daemon (socket or remote host).
  *
 * @Description: DockerScriptRunner
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
public class DockerScriptRunner implements ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerScriptRunner.class);

    private final CronflowScriptProperties props;
    private final ObjectMapper mapper;
    private final SqlScriptExecutor sqlExecutor;
    private final String dockerBin;

    public DockerScriptRunner(CronflowScriptProperties props, ObjectMapper mapper,
            SqlScriptExecutor sqlExecutor) {
        this.props = props;
        this.mapper = mapper;
        this.sqlExecutor = sqlExecutor;
        this.dockerBin = "docker";
    }

    @Override
    public boolean supports(ScriptLanguage language) {
        if (!ScriptLanguages.allowed(props, language)) {
            return false;
        }
        return switch (language) {
            case SHELL, PYTHON, JAVA -> props.getDocker().getImages().get(imageKey(language)) != null;
            case SQL -> sqlExecutor != null && sqlExecutor.hasDatasource();
        };
    }

    @Override
    public ScriptResult run(ScriptSpec spec) {
        if (spec.language() == ScriptLanguage.SQL) {
            return sqlExecutor != null ? sqlExecutor.run(spec)
                    : ScriptResults.error("SQL requested but no datasource is configured");
        }
        String image = props.getDocker().getImages().get(imageKey(spec.language()));
        if (image == null) {
            return ScriptResults.error("No script image configured for " + spec.language());
        }
        boolean copy = props.getDocker().isCopyMode();
        long startedAt = System.currentTimeMillis();
        Path dir = null;
        String container = "script-" + UUID.randomUUID();
        try {
            dir = Files.createTempDirectory(workParent(), "script-");
            Files.writeString(dir.resolve("script." + spec.language().fileExtension()),
                    spec.source() == null ? "" : spec.source(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("input.json"),
                    mapper.writeValueAsString(ScriptIo.inputJson(spec)), StandardCharsets.UTF_8);
            Path out = dir.resolve("stdout.log");
            Path err = dir.resolve("stderr.log");
            long timeout = spec.timeoutMillis() > 0 ? spec.timeoutMillis() : props.getDefaultTimeoutMillis();

            if (copy) {
                // docker cp recreates /script owned by root with the default mode, so a non-root script
                // cannot CREATE result.json there — only overwrite an existing world-writable file. Seed an
                // empty, world-writable result.json so the script's write is an overwrite, not a create.
                Files.writeString(dir.resolve("result.json"), "{}", StandardCharsets.UTF_8);
                // And make every copied file world-writable (docker cp keeps the executor-root ownership).
                makeWorldWritable(dir);
                // Create the container, copy the script in, then start it — so the daemon never needs to
                // see the executor's filesystem (the executor itself runs in a container in production).
                dockerExec("create the container",
                        dockerCommand(spec, image, container, dir, true));
                dockerExec("copy the script in",
                        List.of(dockerBin, "cp", dir.toAbsolutePath() + "/.", container + ":/script"));
            }
            ProcessBuilder pb = new ProcessBuilder(copy
                    ? List.of(dockerBin, "start", "-a", container)
                    : dockerCommand(spec, image, container, dir, false));
            pb.redirectOutput(out.toFile());
            pb.redirectError(err.toFile());

            Process p = pb.start();
            boolean finished = p.waitFor(timeout, TimeUnit.MILLISECONDS);
            boolean timedOut = false;
            int exit;
            if (!finished) {
                kill(container);
                p.destroyForcibly();
                p.waitFor(10, TimeUnit.SECONDS);
                timedOut = true;
                exit = -1;
            } else {
                exit = p.exitValue();
            }
            if (copy && !timedOut) {
                // Pull result.json back out (best effort — a script need not write one).
                copyOut(container, dir.resolve("result.json"));
            }
            String stdout = ScriptIo.readCapped(out, props.getMaxOutputBytes());
            String stderr = ScriptIo.readCapped(err, props.getMaxOutputBytes());
            Map<String, Object> result = ScriptIo.readResultJson(dir.resolve("result.json"), mapper);
            long dur = System.currentTimeMillis() - startedAt;
            return new ScriptResult(!timedOut && exit == 0, exit, timedOut, stdout, stderr, result, dur,
                    null);
        } catch (Exception e) {
            log.warn("docker script run failed for {}", spec.name(), e);
            kill(container);
            return new ScriptResult(false, -1, false, "", "", null,
                    System.currentTimeMillis() - startedAt, e.toString());
        } finally {
            if (copy) {
                removeContainer(container);
            }
            deleteQuietly(dir);
        }
    }

    /** Run a one-shot docker command (create / cp), throwing when it fails so the caller reports it. */
    private void dockerExec(String what, List<String> command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
            p.destroyForcibly();
            throw new IllegalStateException("failed to " + what + ": " + output.strip());
        }
    }

    /** Copy {@code /script/result.json} out of the container to {@code dest}; best effort. */
    private void copyOut(String container, Path dest) {
        try {
            new ProcessBuilder(dockerBin, "cp", container + ":/script/result.json", dest.toString())
                    .redirectErrorStream(true).start().waitFor(20, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // the script wrote no result.json — fine
        }
    }

    /**
     * Make the run dir and its files world-writable so a non-root script (copied in via docker cp,
     * which keeps the root ownership of the executor's files) can still write result.json. No-op on a
     * non-POSIX filesystem.
     */
    private void makeWorldWritable(Path dir) {
        try (var walk = Files.walk(dir)) {
            var all = java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx");
            walk.forEach(p -> {
                try {
                    Files.setPosixFilePermissions(p, all);
                } catch (Exception ignored) {
                    // not POSIX, or not permitted — best effort
                }
            });
        } catch (Exception ignored) {
            // best effort
        }
    }

    private void removeContainer(String container) {
        try {
            new ProcessBuilder(dockerBin, "rm", "-f", container).redirectErrorStream(true).start()
                    .waitFor(20, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // best effort
        }
    }

    private List<String> dockerCommand(ScriptSpec spec, String image, String container, Path dir,
            boolean copy) {
        CronflowScriptProperties.Docker d = props.getDocker();
        List<String> cmd = new ArrayList<>();
        cmd.add(dockerBin);
        // copy-mode builds a container to populate via `docker cp`, then start; bind-mode runs directly.
        if (copy) {
            cmd.add("create");
        } else {
            cmd.add("run");
            cmd.add("--rm");
        }
        cmd.add("--name");
        cmd.add(container);
        if (d.getRuntime() != null && !d.getRuntime().isBlank()) {
            cmd.add("--runtime");
            cmd.add(d.getRuntime());
        }
        cmd.add("--network");
        cmd.add(d.getNetwork());
        cmd.add("--memory");
        cmd.add(d.getMemory());
        cmd.add("--cpus");
        cmd.add(d.getCpus());
        cmd.add("--pids-limit");
        cmd.add(Integer.toString(d.getPidsLimit()));
        cmd.add("-u");
        cmd.add(d.getUser());
        cmd.add("--security-opt");
        cmd.add("no-new-privileges:true");
        if (!copy) {
            // Bind-mount only works when the daemon shares the executor's filesystem; copy-mode does not.
            cmd.add("-v");
            cmd.add(dir.toAbsolutePath() + ":/script");
        }
        cmd.add("-w");
        cmd.add("/script");
        for (var e : ScriptIo.env(spec, "/script").entrySet()) {
            cmd.add("-e");
            cmd.add(e.getKey() + "=" + e.getValue());
        }
        cmd.add(image);
        String interpreter = switch (spec.language()) {
            case SHELL -> props.getShellBin();
            case JAVA -> props.getJavaBin(); // java X.java — single-file source launch (JDK 11+ image)
            default -> "python";
        };
        cmd.add(interpreter);
        cmd.add("/script/script." + spec.language().fileExtension());
        return cmd;
    }

    private void kill(String container) {
        try {
            new ProcessBuilder(dockerBin, "kill", container).redirectErrorStream(true).start()
                    .waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // best effort
        }
    }

    private String imageKey(ScriptLanguage language) {
        return language.name().toLowerCase();
    }

    private Path workParent() throws IOException {
        String base = props.getWorkDir();
        Path parent = base == null || base.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"))
                : Path.of(base);
        Files.createDirectories(parent);
        return parent;
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
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
