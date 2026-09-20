package com.github.cronflow.springapp.executor;

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
import com.github.cronflow.springapp.executor.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.executor.pojo.DagRegistrationRequest;

/**
 * Weaves this executor's DAG definitions once the context is ready and registers them with the
 * cronflow server, then heartbeats on the same schedule (which doubles as re-registration and
 * survives a leader change) — the executor-side lifecycle mirrors cronsmith's registrar.
 *
 * @Description: CronflowClientRegistrar
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class CronflowClientRegistrar
        implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CronflowClientRegistrar.class);

    private final CronflowClientProperties properties;
    private final Environment environment;
    private final DagScanner dagScanner;
    private final CronflowServerClient serverClient;
    private final ExecutorIdentity identity;

    private ScheduledExecutorService scheduler;
    private volatile boolean registered;

    public CronflowClientRegistrar(CronflowClientProperties properties, Environment environment,
            DagScanner dagScanner, CronflowServerClient serverClient, ExecutorIdentity identity) {
        this.properties = properties;
        this.environment = environment;
        this.dagScanner = dagScanner;
        this.serverClient = serverClient;
        this.identity = identity;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long period = Math.max(5L, properties.getRegisterIntervalSeconds());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cronflow-client-registrar");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::registerQuietly, 0, period, TimeUnit.SECONDS);
    }

    private void registerQuietly() {
        try {
            String runUrl = resolveRunUrl();
            String healthUrl = resolveHealthCheckUrl();
            if (!registered) {
                DagScanner.ScanResult scan = dagScanner.scan();
                String instanceId = serverClient.register(new DagRegistrationRequest(application(),
                        identity.getInstanceId(), runUrl, healthUrl, scan.dags(), scan.triggers(),
                        properties.getWeight()));
                if (instanceId != null) {
                    identity.setInstanceId(instanceId);
                    registered = true;
                    log.info("cronflow: registered as {} ({} DAG(s)) with run URL {}", instanceId,
                            scan.dags().size(), runUrl);
                }
            } else {
                boolean known = serverClient.heartbeat(new DagHeartbeatRequest(application(),
                        identity.getInstanceId(), runUrl, healthUrl, properties.getWeight()));
                if (!known) {
                    // The server no longer knows this instance (e.g. the cluster restarted and lost its
                    // in-memory registry). Drop back to a full re-register next tick so the DAG's live
                    // host mapping is rebuilt and the graph is runnable again.
                    registered = false;
                    log.info("cronflow: server does not recognise this instance; will re-register");
                }
            }
        } catch (RuntimeException e) {
            registered = false; // fall back to a full re-register next tick
            log.debug("cronflow: registration/heartbeat tick failed: {}", e.toString());
        }
    }

    private String application() {
        if (properties.getApplication() != null && !properties.getApplication().isBlank()) {
            return properties.getApplication();
        }
        String name = environment.getProperty("spring.application.name");
        return name != null && !name.isBlank() ? name : "cronflow-executor";
    }

    /** baseUrl (verbatim) else scheme://host:port, then the node-run path. */
    private String resolveRunUrl() {
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            return trimTrailingSlash(properties.getBaseUrl()) + NodeRunController.RUN_NODE_PATH;
        }
        return properties.getScheme() + "://" + host() + ":" + port() + NodeRunController.RUN_NODE_PATH;
    }

    private String resolveHealthCheckUrl() {
        if (properties.getHealthCheckUrl() != null && !properties.getHealthCheckUrl().isBlank()) {
            return properties.getHealthCheckUrl();
        }
        // TODO: mirror cronsmith's actuator-vs-ping / management-port detection.
        return properties.getScheme() + "://" + host() + ":" + port() + "/actuator/health";
    }

    private String host() {
        if (properties.getAdvertiseHost() != null && !properties.getAdvertiseHost().isBlank()) {
            return properties.getAdvertiseHost();
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private int port() {
        if (properties.getAdvertisePort() != null) {
            return properties.getAdvertisePort();
        }
        String p = environment.getProperty("local.server.port", environment.getProperty("server.port"));
        try {
            return p != null ? Integer.parseInt(p) : 8080;
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    private static String trimTrailingSlash(String base) {
        String s = base.trim();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

}
