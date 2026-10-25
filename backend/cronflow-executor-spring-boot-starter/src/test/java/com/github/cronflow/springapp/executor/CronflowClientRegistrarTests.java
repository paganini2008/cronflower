package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.ReflectionUtils;

/**
 * Covers the executor-side registrar: URL/host/port/application resolution and the
 * register-then-heartbeat reconcile loop (including re-registration when the server forgets the
 * instance and the swallowed-error path). Private methods are reached by reflection with mocks.
 */
class CronflowClientRegistrarTests {

    private GenericApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new GenericApplicationContext();
        ctx.refresh();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private CronflowClientProperties props() {
        CronflowClientProperties p = new CronflowClientProperties();
        p.setApplication("orders");
        p.setAdvertiseHost("10.0.0.5");
        p.setAdvertisePort(9090);
        return p;
    }

    private CronflowClientRegistrar registrar(CronflowClientProperties p, MockEnvironment env,
            CronflowServerClient client) {
        return new CronflowClientRegistrar(p, env, new DagScanner(ctx, "orders"), client,
                new ExecutorIdentity());
    }

    private Object invoke(CronflowClientRegistrar r, String name) {
        Method m = ReflectionUtils.findMethod(CronflowClientRegistrar.class, name);
        ReflectionUtils.makeAccessible(m);
        return ReflectionUtils.invokeMethod(m, r);
    }

    @Test
    void resolvesRunAndHealthUrlsFromHostPort() {
        CronflowClientRegistrar r =
                registrar(props(), new MockEnvironment(), mock(CronflowServerClient.class));
        assertThat(invoke(r, "resolveRunUrl"))
                .isEqualTo("http://10.0.0.5:9090" + NodeRunController.RUN_NODE_PATH);
        assertThat(invoke(r, "resolveHealthCheckUrl"))
                .isEqualTo("http://10.0.0.5:9090/actuator/health");
    }

    @Test
    void baseUrlAndHealthUrlOverridesWin() {
        CronflowClientProperties p = props();
        p.setBaseUrl("https://gw/orders/");
        p.setHealthCheckUrl("https://gw/health");
        CronflowClientRegistrar r =
                registrar(p, new MockEnvironment(), mock(CronflowServerClient.class));
        assertThat(invoke(r, "resolveRunUrl"))
                .isEqualTo("https://gw/orders" + NodeRunController.RUN_NODE_PATH);
        assertThat(invoke(r, "resolveHealthCheckUrl")).isEqualTo("https://gw/health");
    }

    @Test
    void applicationFallsBackToSpringNameThenDefault() {
        CronflowClientProperties p = new CronflowClientProperties(); // no application
        MockEnvironment env = new MockEnvironment().withProperty("spring.application.name", "fromEnv");
        CronflowClientRegistrar r = registrar(p, env, mock(CronflowServerClient.class));
        assertThat(invoke(r, "application")).isEqualTo("fromEnv");

        CronflowClientRegistrar noName =
                registrar(new CronflowClientProperties(), new MockEnvironment(),
                        mock(CronflowServerClient.class));
        assertThat(invoke(noName, "application")).isEqualTo("cronflow-executor");
    }

    @Test
    void portFallsBackToEnvironmentThenDefault() {
        CronflowClientProperties p = new CronflowClientProperties(); // no advertisePort
        MockEnvironment env = new MockEnvironment().withProperty("server.port", "7000");
        CronflowClientRegistrar r = registrar(p, env, mock(CronflowServerClient.class));
        assertThat(invoke(r, "resolveRunUrl").toString()).contains(":7000");

        CronflowClientRegistrar noPort = registrar(new CronflowClientProperties(),
                new MockEnvironment(), mock(CronflowServerClient.class));
        assertThat(noPort).isNotNull();
        assertThat(invoke(noPort, "resolveRunUrl").toString()).contains(":8080");
    }

    @Test
    void reconcileLoopRegistersThenHeartbeatsThenReRegisters() {
        CronflowServerClient client = mock(CronflowServerClient.class);
        when(client.register(any())).thenReturn("i-1");
        when(client.heartbeat(any())).thenReturn(true, false); // known, then forgotten
        ExecutorIdentity identity = new ExecutorIdentity();
        CronflowClientRegistrar r = new CronflowClientRegistrar(props(), new MockEnvironment(),
                new DagScanner(ctx, "orders"), client, identity);

        Method tick = ReflectionUtils.findMethod(CronflowClientRegistrar.class, "registerQuietly");
        ReflectionUtils.makeAccessible(tick);

        ReflectionUtils.invokeMethod(tick, r); // first: register
        assertThat(identity.getInstanceId()).isEqualTo("i-1");
        ReflectionUtils.invokeMethod(tick, r); // heartbeat -> known
        ReflectionUtils.invokeMethod(tick, r); // heartbeat -> forgotten -> registered=false
        ReflectionUtils.invokeMethod(tick, r); // register again
        verify(client, times(2)).register(any());
        verify(client, times(2)).heartbeat(any());
    }

    @Test
    void registerFailureLeavesUnregistered() {
        CronflowServerClient client = mock(CronflowServerClient.class);
        when(client.register(any())).thenThrow(new RuntimeException("server down"));
        CronflowClientRegistrar r = new CronflowClientRegistrar(props(), new MockEnvironment(),
                new DagScanner(ctx, "orders"), client, new ExecutorIdentity());
        Method tick = ReflectionUtils.findMethod(CronflowClientRegistrar.class, "registerQuietly");
        ReflectionUtils.makeAccessible(tick);
        ReflectionUtils.invokeMethod(tick, r); // must not propagate
        verify(client).register(any());
    }

    @Test
    void registerReturningNullStaysUnregistered() {
        CronflowServerClient client = mock(CronflowServerClient.class);
        when(client.register(any())).thenReturn(null);
        ExecutorIdentity identity = new ExecutorIdentity();
        CronflowClientRegistrar r = new CronflowClientRegistrar(props(), new MockEnvironment(),
                new DagScanner(ctx, "orders"), client, identity);
        Method tick = ReflectionUtils.findMethod(CronflowClientRegistrar.class, "registerQuietly");
        ReflectionUtils.makeAccessible(tick);
        ReflectionUtils.invokeMethod(tick, r);
        ReflectionUtils.invokeMethod(tick, r); // still not registered -> register attempted twice
        assertThat(identity.getInstanceId()).isNull();
        verify(client, times(2)).register(any());
    }

    @Test
    void lifecycleStartAndDestroy() {
        CronflowClientRegistrar r =
                registrar(props(), new MockEnvironment(), mock(CronflowServerClient.class));
        r.onApplicationEvent(null);
        r.destroy();
        // destroy is safe without a start too
        registrar(props(), new MockEnvironment(), mock(CronflowServerClient.class)).destroy();
    }
}
