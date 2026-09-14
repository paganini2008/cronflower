package com.github.cronflow.springapp.executor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Configuration for the cronflow executor (workflow node runner) side.
 *
 * <p>
 * Deliberately mirrors {@code cronsmith.client.*}: a cronflow executor is, like a cronsmith executor,
 * a callee that registers to the server and is dialled back over HTTP — here to run a DAG node bean
 * method rather than a scheduled task.
 *
 * @Description: CronflowClientProperties
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Data
@ConfigurationProperties("cronflow.client")
public class CronflowClientProperties {

    /** Master switch. Turn off to disable DAG registration and the node-run endpoint entirely. */
    private boolean enabled = true;

    /**
     * Server base URLs, e.g. {@code http://host:8080}. Any one is enough (writes route to the leader).
     * Defaults to {@code http://localhost:19090} (a local scheduler) when left unset.
     */
    private List<String> serverUrls = new ArrayList<>(List.of("http://localhost:19090"));

    /** The cronflow server's REST API prefix (must match {@code cronflow.server.api-prefix}). Default {@code /cronflow}. */
    private String serverApiPrefix = "/cronflow";

    /** Executor application name. Defaults to {@code spring.application.name}. */
    private String application;

    /** Address peers can dial to reach this executor. Defaults to the local host address. */
    private String advertiseHost;

    /** Port peers can dial. Defaults to the running web server port. */
    private Integer advertisePort;

    /** URL scheme used to build this executor's node-run endpoint. */
    private String scheme = "http";

    /** Full external base URL, used verbatim to build the node-run URL when set (reverse proxy / NAT). */
    private String baseUrl;

    /** Full external health-check URL, used verbatim when set. */
    private String healthCheckUrl;

    /** How often to re-register. Doubles as a heartbeat and survives a leader change. */
    private long registerIntervalSeconds = 30L;

    /** Routing weight for this executor (server's WEIGHTED dispatch). Default {@code 1}. */
    private int weight = 1;

    /** Connect timeout, in milliseconds, for calls back to the server. */
    private int connectTimeoutMillis = 3000;

    /** Read timeout, in milliseconds, for calls back to the server. */
    private int readTimeoutMillis = 10000;

    /** Size of the thread pool that runs node methods. */
    private int invokerPoolSize = 8;

    /** Extra HTTP headers to send on every call back to the server. */
    private Map<String, String> headers = new LinkedHashMap<>();

}
