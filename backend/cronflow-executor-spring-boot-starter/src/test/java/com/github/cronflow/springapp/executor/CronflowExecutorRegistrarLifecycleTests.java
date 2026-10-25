package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.ReflectionUtils;

/**
 * Covers the scheduler-executor registrar's register-then-heartbeat reconcile loop, application-name
 * resolution, and the start/destroy lifecycle. The register tick is driven directly (no 5s wait).
 */
class CronflowExecutorRegistrarLifecycleTests {

    private CronflowExecutorProperties props() {
        CronflowExecutorProperties p = new CronflowExecutorProperties();
        p.setApplication("orders");
        p.setAdvertiseHost("10.0.0.5");
        p.setAdvertisePort(9090);
        return p;
    }

    private CronflowExecutorRegistrar registrar(CronflowExecutorProperties p, MockEnvironment env,
            CronflowExecutorServerClient client, TaskRegistry registry) {
        return new CronflowExecutorRegistrar(p, env, registry, client, new ExecutorIdentity());
    }

    private void tick(CronflowExecutorRegistrar r) {
        Method m = ReflectionUtils.findMethod(CronflowExecutorRegistrar.class, "registerQuietly");
        ReflectionUtils.makeAccessible(m);
        ReflectionUtils.invokeMethod(m, r);
    }

    @SuppressWarnings("unchecked")
    private TaskRegistry emptyRegistry() {
        TaskRegistry registry = mock(TaskRegistry.class);
        when(registry.scan(anyString())).thenReturn(List.of());
        return registry;
    }

    @Test
    void registersThenHeartbeats() {
        CronflowExecutorServerClient client = mock(CronflowExecutorServerClient.class);
        when(client.register(any())).thenReturn("assigned-1");
        when(client.heartbeat(any())).thenReturn(true);
        ExecutorIdentity identity = new ExecutorIdentity();
        CronflowExecutorRegistrar r = new CronflowExecutorRegistrar(props(), new MockEnvironment(),
                emptyRegistry(), client, identity);
        tick(r); // register
        assertThat(identity.getInstanceId()).isEqualTo("assigned-1");
        assertThat(identity.repr()).contains("orders").contains("assigned-1");
        tick(r); // heartbeat
        verify(client, times(1)).register(any());
        verify(client, times(1)).heartbeat(any());
    }

    @Test
    void registerReturningNullRetries() {
        CronflowExecutorServerClient client = mock(CronflowExecutorServerClient.class);
        when(client.register(any())).thenReturn(null);
        CronflowExecutorRegistrar r = registrar(props(), new MockEnvironment(), client,
                emptyRegistry());
        tick(r);
        tick(r);
        verify(client, times(2)).register(any());
    }

    @Test
    void errorDuringTickIsSwallowed() {
        CronflowExecutorServerClient client = mock(CronflowExecutorServerClient.class);
        when(client.register(any())).thenThrow(new RuntimeException("server down"));
        CronflowExecutorRegistrar r = registrar(props(), new MockEnvironment(), client,
                emptyRegistry());
        tick(r); // must not propagate
        verify(client).register(any());
    }

    @Test
    void applicationNameFallsBackToEnvironmentThenDefault() {
        CronflowExecutorServerClient client = mock(CronflowExecutorServerClient.class);
        when(client.register(any())).thenReturn("i");
        CronflowExecutorProperties noApp = new CronflowExecutorProperties();
        noApp.setAdvertiseHost("h");
        noApp.setAdvertisePort(1);
        MockEnvironment env = new MockEnvironment().withProperty("spring.application.name", "fromEnv");
        CronflowExecutorRegistrar r = registrar(noApp, env, client, emptyRegistry());
        Method m = ReflectionUtils.findMethod(CronflowExecutorRegistrar.class,
                "resolveApplicationName");
        ReflectionUtils.makeAccessible(m);
        assertThat(ReflectionUtils.invokeMethod(m, r)).isEqualTo("fromEnv");

        CronflowExecutorRegistrar noName =
                registrar(noApp, new MockEnvironment(), client, emptyRegistry());
        assertThat(ReflectionUtils.invokeMethod(m, noName)).isEqualTo("cronflow-executor");
    }

    @Test
    void lifecycleStartAndDestroy() {
        CronflowExecutorRegistrar r = registrar(props(), new MockEnvironment(),
                mock(CronflowExecutorServerClient.class), emptyRegistry());
        r.onApplicationEvent(null);
        r.destroy();
    }
}
