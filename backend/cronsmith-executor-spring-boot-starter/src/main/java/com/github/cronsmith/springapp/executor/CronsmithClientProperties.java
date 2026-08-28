package com.github.cronsmith.springapp.executor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Configuration for the executor side.
 *
 * @Description: CronsmithClientProperties
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@Data
@ConfigurationProperties("cronsmith.client")
public class CronsmithClientProperties {

    /** Master switch. Turn off to disable registration and the run endpoint entirely. */
    private boolean enabled = true;

    /**
     * Server base URLs, e.g. {@code http://host:8080}. More than one is allowed but any of them is
     * enough: every write is routed to the leader, so which node receives it makes no difference.
     * Defaults to {@code http://localhost:19090} (a local scheduler) when left unset.
     */
    private List<String> serverUrls = new ArrayList<>(List.of("http://localhost:19090"));

    /**
     * The server's REST API prefix (i.e. {@code cronsmith.server.api-prefix} on the scheduler),
     * prepended to the register / heartbeat / complete calls. Must match the scheduler's value.
     * Default {@code /cronsmith}; blank or {@code /} means the server serves the API at the root.
     */
    private String serverApiPrefix = "/cronsmith";

    /** Executor application name. Defaults to {@code spring.application.name}. */
    private String application;

    /** Address peers can dial to reach this executor. Defaults to the local host address. */
    private String advertiseHost;

    /** Port peers can dial. Defaults to the running web server port. */
    private Integer advertisePort;

    /** URL scheme used to build this executor's endpoint. */
    private String scheme = "http";

    /**
     * Full external base URL up to and including any context / servlet / base path, e.g.
     * {@code https://gateway/orders-executor}. When set it is used verbatim to build the run URL
     * ({@code base-url + /cronsmith/run}) and overrides scheme/host/port/path auto-detection — use
     * it when the executor sits behind a reverse proxy or a rewritten path.
     */
    private String baseUrl;

    /**
     * Full external health-check URL. When set it is used verbatim and overrides all auto-detection
     * (actuator vs ping, management port, base paths).
     */
    private String healthCheckUrl;

    /** How often to re-register. This doubles as a heartbeat and survives a leader change. */
    private long registerIntervalSeconds = 30L;

    /**
     * Routing weight for this executor, used by the server's {@code WEIGHTED} dispatch strategy: a
     * stronger machine can take proportionally more work. Default {@code 1} (even).
     */
    private int weight = 1;

    /** Connect timeout, in milliseconds, for calls back to the server. */
    private int connectTimeoutMillis = 3000;

    /** Read timeout, in milliseconds, for calls back to the server. */
    private int readTimeoutMillis = 10000;

    /** Size of the thread pool that runs task methods. */
    private int invokerPoolSize = 8;

    /**
     * Extra HTTP headers to send on every call back to the server (register / heartbeat / complete),
     * e.g. an {@code Authorization} token the server requires.
     */
    private Map<String, String> headers = new LinkedHashMap<>();

}
