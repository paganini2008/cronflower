package com.github.cronflow.springapp.server;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Configuration for the cronflow server side.
 *
 * @Description: CronflowServerProperties
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Data
@ConfigurationProperties("cronflow.server")
public class CronflowServerProperties {

    /** Master switch. Off means cronflow does not load at all — a cronsmith-only server is untouched. */
    private boolean enabled = true;

    /**
     * Deployment environment label shown in the console header ({@code dev} / {@code prod}). Set per
     * environment in the staged {@code server.properties} (from application-dev/prod.properties); the
     * jar default is {@code dev}.
     */
    private String env = "dev";

    /**
     * REST API prefix for the cronflow endpoints (registration). Must match the executor's
     * {@code cronflow.client.server-api-prefix}. Default {@code /cronflow}; blank or {@code /} serves
     * at the root. Scoped to the cronflow controllers only, so it never affects cronsmith's paths.
     */
    private String apiPrefix = "/cronflow";

    /** Format for storing DAG definitions in {@code cf_task_dag}: {@code json} (default) or {@code yaml}. */
    private String definitionFormat = "json";

    /** Default per-node HTTP dispatch timeout, in milliseconds, when a node declares none. */
    private long defaultNodeTimeoutMillis = 30000L;

    /** How long an executor instance is considered live after its last heartbeat, in seconds. */
    private long executorTtlSeconds = 60L;

}
