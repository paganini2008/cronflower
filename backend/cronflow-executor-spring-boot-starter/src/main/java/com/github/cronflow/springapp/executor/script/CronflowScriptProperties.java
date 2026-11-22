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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Executor-side configuration for the script (script) feature. It is OFF by default: a script task or node
 * dispatched to an executor with {@code cronflow.script.enabled=false} is rejected rather than run. The
 * runner is Docker-first ({@code docker}) with a {@code local} host-process fallback for executors
 * without Docker.
  *
 * @Description: CronflowScriptProperties
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
@Data
@ConfigurationProperties("cronflow.script")
public class CronflowScriptProperties {

    /** Master switch. Off means this executor refuses to run any script. */
    private boolean enabled = false;

    /** Which runner this executor uses: {@code docker} (default, per-run container) or {@code local} (host process). */
    private String runner = "docker";

    /** Allowed languages on this executor. Empty means all supported. */
    private List<String> languages = List.of();

    /** Default wall-clock budget when a task or node does not set a timeout. */
    private long defaultTimeoutMillis = 60_000L;

    /** Max captured stdout or stderr bytes kept per stream. */
    private int maxOutputBytes = 1_000_000;

    /** Shell interpreter for {@code SHELL} scripts (local runner, and the shell image entrypoint). */
    private String shellBin = "bash";

    /** Python interpreter for {@code PYTHON} scripts (local runner). */
    private String pythonBin = "python3";

    /** Java launcher for {@code JAVA} scripts — run as a single-file source program ({@code java X.java},
     *  JDK 11+). The local runner uses it; set to a JDK 11+ {@code java} on the executor's PATH. */
    private String javaBin = "java";

    /** Base working directory for per-run temp dirs. Defaults to the system temp dir when blank. */
    private String workDir = "";

    private final Docker docker = new Docker();

    private final Sql sql = new Sql();

    /** Docker runner settings: per-language images and per-run resource and isolation limits. */
    @Data
    public static class Docker {

        /** Image per language, e.g. {@code shell -> cronflower/script-shell:1}. */
        private Map<String, String> images = defaultImages();

        /** Memory limit per run, e.g. {@code 512m}. */
        private String memory = "512m";

        /** CPU limit per run, e.g. {@code 1.0}. */
        private String cpus = "1.0";

        /** Network mode: {@code none} (default, no network), {@code bridge}, or a named network. */
        private String network = "none";

        /** Max process count inside the container. */
        private int pidsLimit = 256;

        /** Non-root uid:gid the script runs as inside the container. */
        private String user = "1000:1000";

        /** Optional container runtime (Docker {@code --runtime}) for a hardened sandbox; blank uses the default. */
        private String runtime = "";

        /**
         * Copy the script into the container (docker cp) instead of bind-mounting a host directory.
         * Default true because it is correct everywhere: it is REQUIRED when the Docker daemon cannot see
         * the executor's filesystem (the executor runs in a container and talks to the host/remote daemon,
         * the mainstream production case, or a remote {@code DOCKER_HOST=ssh://…}), and it also works fine
         * when the executor is a bare process on the Docker host. Set it false only to shave the small
         * create+cp+cp overhead off that bare-process case, where a bind-mount is also valid.
         */
        private boolean copyMode = true;

        /** Stock public images per language, so SCRIPT tasks run out of the box once these are pullable. */
        private static Map<String, String> defaultImages() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("shell", "bash:5");
            m.put("python", "python:3-slim");
            m.put("java", "eclipse-temurin:21");
            return m;
        }
    }

    /** SQL runner settings: named datasources a SQL script may target. */
    @Data
    public static class Sql {

        /** Named JDBC datasources, by name, that SQL scripts may reference. */
        private Map<String, Datasource> datasources = new LinkedHashMap<>();

        /** The datasource used when a SQL script names none. */
        private String defaultDatasource = "";

        @Data
        public static class Datasource {
            private String url;
            private String driver = "";
            private String username = "";
            private String password = "";
            private boolean readOnly = false;
            private int maxRows = 10_000;
            private int queryTimeoutSeconds = 30;
        }
    }
}
