package com.github.cronsmith.springapp.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.chaconneai.spreader.GossipCluster;
import com.github.cronsmith.springapp.scheduler.TaskReflectionUtils;
import com.github.cronsmith.springapp.scheduler.CronsmithServerProperties.Dispatch;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry.ExecutorInstance;

/**
 * Brings the server side up once the application is ready: installs the executor-dispatching task
 * factory, claims the cluster channels, and starts the scheduler on the leader. Also runs a periodic
 * sweep that evicts executors past their TTL and, on the leader, health-checks the rest.
 *
 * @Description: CronsmithServerBootstrap
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class CronsmithServerBootstrap
        implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CronsmithServerBootstrap.class);

    private final ClusterTaskManager clusterTaskManager;
    private final ClusterExecutorRegistry clusterExecutorRegistry;
    private final ExecutorRegistry registry;
    private final DefaultTaskDispatcher dispatcher;
    private final SchedulerLifecycle schedulerLifecycle;
    private final GossipCluster cluster;
    private final long sweepMillis;
    private final RestClient healthClient;

    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cronsmith-sweeper");
        t.setDaemon(true);
        return t;
    });

    public CronsmithServerBootstrap(ClusterTaskManager clusterTaskManager,
            ClusterExecutorRegistry clusterExecutorRegistry, ExecutorRegistry registry,
            DefaultTaskDispatcher dispatcher, SchedulerLifecycle schedulerLifecycle,
            GossipCluster cluster, Dispatch dispatch) {
        this.clusterTaskManager = clusterTaskManager;
        this.clusterExecutorRegistry = clusterExecutorRegistry;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.schedulerLifecycle = schedulerLifecycle;
        this.cluster = cluster;
        this.sweepMillis = Math.max(10000L, dispatch.getExecutorTtlMillis() / 3);
        SimpleClientHttpRequestFactory factory = HttpRequestFactories
                .create(dispatch.getConnectTimeoutMillis(), dispatch.getConnectTimeoutMillis());
        this.healthClient = RestClient.builder().requestFactory(factory).defaultHeaders(headers -> {
            if (dispatch.getHeaders() != null) {
                dispatch.getHeaders().forEach(headers::add);
            }
        }).build();

        // Set as early as possible, so a task that fires right after leadership is decided already
        // finds the dispatcher and the executor-dispatching factory.
        TaskReflectionUtils.setTaskFactory(new HttpDispatchCustomTaskFactory());
        TaskDispatcherHolder.set(dispatcher);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        clusterTaskManager.start();
        clusterExecutorRegistry.start();
        dispatcher.start();
        schedulerLifecycle.reconcile();
        sweeper.scheduleWithFixedDelay(this::sweep, sweepMillis, sweepMillis, TimeUnit.MILLISECONDS);
        log.info("cronsmith server started (leader={})", cluster.isLeader());
    }

    private void sweep() {
        try {
            registry.evictStale();
            if (cluster.isLeader()) {
                for (ExecutorInstance instance : registry.snapshot()) {
                    if (!isHealthy(instance)) {
                        log.info("Executor {} failed health check; removing", instance.instanceId());
                        clusterExecutorRegistry.remove(instance);
                    }
                }
            }
            // Re-check leadership in case it changed without an event reaching us yet. The scheduler's
            // own windowed claim loop picks up due tasks; nothing to reconcile here.
            schedulerLifecycle.reconcile();
        } catch (Exception e) {
            log.warn("Executor sweep failed", e);
        }
    }

    private boolean isHealthy(ExecutorInstance instance) {
        if (instance.healthCheckUrl() == null || instance.healthCheckUrl().isBlank()) {
            return true;
        }
        try {
            healthClient.get().uri(instance.healthCheckUrl()).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void destroy() {
        sweeper.shutdownNow();
        schedulerLifecycle.onSelfStopped(cluster.self());
    }

}
