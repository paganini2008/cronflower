package com.github.cronsmith.springapp.executor;

import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Registers this executor with the server once the application is up, then keeps re-registering on a
 * fixed interval. The periodic call doubles as a heartbeat and lets the executor reappear after a
 * server restart or a leader change without any coordination.
 *
 * <p>
 * Both the run URL and the health URL are resolved to full external URLs, honouring:
 * <ul>
 * <li>servlet {@code server.servlet.context-path} and {@code spring.mvc.servlet.path};</li>
 * <li>reactive {@code spring.webflux.base-path};</li>
 * <li>actuator {@code management.endpoints.web.base-path}, and a separate
 * {@code management.server.port} / {@code management.server.base-path}.</li>
 * </ul>
 * For anything these do not capture — a reverse proxy, a rewritten path — set
 * {@code cronsmith.client.base-url} and/or {@code cronsmith.client.health-check-url} explicitly.
 *
 * <p>
 * The liveness URL is chosen automatically: {@code /actuator/health} when Spring Boot Actuator is on
 * the classpath, otherwise this starter's own {@code /cronsmith/ping}.
 *
 * @Description: CronsmithClientRegistrar
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class CronsmithClientRegistrar implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CronsmithClientRegistrar.class);

    // The actuator HealthEndpoint moved packages in Boot 4 (spring-boot-health module); accept both.
    private static final String[] HEALTH_ENDPOINT_CLASSES = {
            "org.springframework.boot.health.actuate.endpoint.HealthEndpoint", // Boot 4+
            "org.springframework.boot.actuate.health.HealthEndpoint" // Boot 3
    };

    private final CronsmithClientProperties properties;
    private final Environment environment;
    private final TaskRegistry taskRegistry;
    private final CronsmithServerClient serverClient;
    private final ExecutorIdentity identity;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cronsmith-registrar");
                t.setDaemon(true);
                return t;
            });

    /** False until the initial task-carrying registration has been accepted at least once. */
    private volatile boolean registered = false;

    public CronsmithClientRegistrar(CronsmithClientProperties properties, Environment environment,
            TaskRegistry taskRegistry, CronsmithServerClient serverClient, ExecutorIdentity identity) {
        this.properties = properties;
        this.environment = environment;
        this.taskRegistry = taskRegistry;
        this.serverClient = serverClient;
        this.identity = identity;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long interval = Math.max(5L, properties.getRegisterIntervalSeconds());
        // Runs off the main thread, so a slow or unreachable server never blocks startup.
        scheduler.scheduleWithFixedDelay(this::registerQuietly, 0L, interval, TimeUnit.SECONDS);
    }

    private void registerQuietly() {
        try {
            String application = resolveApplicationName();
            // The instanceId is the scheduler's to assign: send null on the first registration and the
            // id it gave us on every call after, so it refreshes the same registry entry.
            String instanceId = identity.getInstanceId();
            String dispatcherBase = resolveDispatcherBaseUrl();
            String runUrl = dispatcherBase + CronsmithClientController.RUN_PATH;
            String healthUrl = resolveHealthCheckUrl(dispatcherBase);
            if (!registered) {
                // Startup: send the full task list once. Keep retrying until a server accepts it,
                // then switch to lightweight heartbeats. Tasks are reconciled only at (re)start.
                RegistrationRequest request = new RegistrationRequest(application, instanceId, runUrl,
                        healthUrl, taskRegistry.scan(application), properties.getWeight());
                String assignedId = serverClient.register(request);
                if (assignedId != null) {
                    registered = true;
                    identity.setInstanceId(assignedId);
                    identity.setRepr(application + "(" + assignedId + "@" + resolveHost() + ":"
                            + resolvePort() + ")");
                    log.info("Registered executor {} ({}) -> run {} health {} with {} task(s)",
                            application, assignedId, runUrl, healthUrl, request.tasks().size());
                }
            } else {
                // Heartbeat: keep this executor present and reachable; no task list.
                serverClient.heartbeat(new HeartbeatRequest(application, instanceId, runUrl,
                        healthUrl, properties.getWeight()));
            }
        } catch (Exception e) {
            log.warn("Executor registration/heartbeat failed", e);
        }
    }

    private String resolveApplicationName() {
        if (StringUtils.hasText(properties.getApplication())) {
            return properties.getApplication();
        }
        String name = environment.getProperty("spring.application.name");
        return StringUtils.hasText(name) ? name : "cronsmith-executor";
    }

    /**
     * The base URL our own controllers (run, ping) live under: {@code scheme://host:port} plus any
     * container prefix and dispatcher path. Overridden wholesale by {@code cronsmith.client.base-url}.
     */
    private String resolveDispatcherBaseUrl() {
        if (StringUtils.hasText(properties.getBaseUrl())) {
            return trimTrailingSlash(properties.getBaseUrl());
        }
        return authority() + dispatcherPrefix();
    }

    private String resolveHealthCheckUrl(String dispatcherBaseUrl) {
        if (StringUtils.hasText(properties.getHealthCheckUrl())) {
            return properties.getHealthCheckUrl();
        }
        if (!actuatorHealthPresent()) {
            // Our ping lives next to the run endpoint, so it shares the dispatcher prefix.
            return dispatcherBaseUrl + CronsmithPingController.PING_PATH;
        }
        String endpointsBase =
                normalizePrefix(environment.getProperty("management.endpoints.web.base-path",
                        "/actuator"));
        Integer managementPort = environment.getProperty("management.server.port", Integer.class);
        if (managementPort != null && managementPort > 0 && managementPort != resolvePort()) {
            // Management runs on its own port: its own base path, no app context/servlet prefix.
            String managementBase =
                    normalizePrefix(environment.getProperty("management.server.base-path", ""));
            return properties.getScheme() + "://" + resolveHost() + ":" + managementPort
                    + managementBase + endpointsBase + "/health";
        }
        // Management shares the main port: honours the container prefix but not the servlet path.
        return authority() + containerPrefix() + endpointsBase + "/health";
    }

    private boolean actuatorHealthPresent() {
        for (String className : HEALTH_ENDPOINT_CLASSES) {
            if (ClassUtils.isPresent(className, getClass().getClassLoader())) {
                return true;
            }
        }
        return false;
    }

    private String authority() {
        return properties.getScheme() + "://" + resolveHost() + ":" + resolvePort();
    }

    /** context-path (servlet) or base-path (reactive). Applies to actuator on the main port. */
    private String containerPrefix() {
        String webflux = environment.getProperty("spring.webflux.base-path");
        if (StringUtils.hasText(webflux)) {
            return normalizePrefix(webflux);
        }
        return normalizePrefix(environment.getProperty("server.servlet.context-path", ""));
    }

    /** Container prefix plus the servlet dispatcher path. Applies to our own controllers. */
    private String dispatcherPrefix() {
        String webflux = environment.getProperty("spring.webflux.base-path");
        if (StringUtils.hasText(webflux)) {
            return normalizePrefix(webflux);
        }
        return normalizePrefix(environment.getProperty("server.servlet.context-path", ""))
                + normalizePrefix(environment.getProperty("spring.mvc.servlet.path", ""));
    }

    private String resolveHost() {
        return StringUtils.hasText(properties.getAdvertiseHost()) ? properties.getAdvertiseHost()
                : localHostAddress();
    }

    private int resolvePort() {
        if (properties.getAdvertisePort() != null) {
            return properties.getAdvertisePort();
        }
        // local.server.port is set once the (servlet or reactive) web server has started.
        Integer live = environment.getProperty("local.server.port", Integer.class);
        return live != null ? live : environment.getProperty("server.port", Integer.class, 8080);
    }

    private String localHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /** Leading slash, no trailing slash; a bare "/" or blank collapses to "". */
    private static String normalizePrefix(String path) {
        if (path == null) {
            return "";
        }
        String s = path.trim();
        if (s.isEmpty() || s.equals("/")) {
            return "";
        }
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

}
