package com.github.cronsmith.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.ReflectionUtils;

/**
 * Covers the prefix-aware URL resolution in the registrar: servlet context/servlet path, WebFlux
 * base path, an explicit base-url override, and the actuator health URL (shared vs separate
 * management port). The resolution methods are private, so they are reached by reflection with a
 * {@link MockEnvironment}.
 */
class CronsmithClientRegistrarUrlTests {

    private CronsmithClientRegistrar registrar(CronsmithClientProperties props, MockEnvironment env) {
        return new CronsmithClientRegistrar(props, env, new TaskRegistry(null), null,
                new ExecutorIdentity());
    }

    private CronsmithClientProperties props() {
        CronsmithClientProperties p = new CronsmithClientProperties();
        p.setAdvertiseHost("10.0.0.5");
        p.setAdvertisePort(9090);
        return p;
    }

    private String dispatcherBase(CronsmithClientRegistrar r) {
        Method m = ReflectionUtils.findMethod(CronsmithClientRegistrar.class,
                "resolveDispatcherBaseUrl");
        ReflectionUtils.makeAccessible(m);
        return (String) ReflectionUtils.invokeMethod(m, r);
    }

    private String healthUrl(CronsmithClientRegistrar r, String base) {
        Method m = ReflectionUtils.findMethod(CronsmithClientRegistrar.class,
                "resolveHealthCheckUrl", String.class);
        ReflectionUtils.makeAccessible(m);
        return (String) ReflectionUtils.invokeMethod(m, r, base);
    }

    @Test
    void plainAuthorityWithNoPrefixes() {
        assertThat(dispatcherBase(registrar(props(), new MockEnvironment())))
                .isEqualTo("http://10.0.0.5:9090");
    }

    @Test
    void honoursServletContextAndServletPath() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("server.servlet.context-path", "/app")
                .withProperty("spring.mvc.servlet.path", "/api");
        assertThat(dispatcherBase(registrar(props(), env))).isEqualTo("http://10.0.0.5:9090/app/api");
    }

    @Test
    void honoursWebfluxBasePath() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.webflux.base-path", "/rx");
        assertThat(dispatcherBase(registrar(props(), env))).isEqualTo("http://10.0.0.5:9090/rx");
    }

    @Test
    void baseUrlOverrideWins() {
        CronsmithClientProperties p = props();
        p.setBaseUrl("https://gateway/orders-executor/");
        assertThat(dispatcherBase(registrar(p, new MockEnvironment())))
                .isEqualTo("https://gateway/orders-executor");
    }

    @Test
    void healthCheckUrlOverrideWins() {
        CronsmithClientProperties p = props();
        p.setHealthCheckUrl("https://gw/health");
        assertThat(healthUrl(registrar(p, new MockEnvironment()), "http://10.0.0.5:9090"))
                .isEqualTo("https://gw/health");
    }

    @Test
    void actuatorHealthOnSeparateManagementPort() {
        // Actuator is on the test classpath, so the actuator branch (not the ping fallback) is taken.
        MockEnvironment env = new MockEnvironment().withProperty("management.server.port", "9091");
        assertThat(healthUrl(registrar(props(), env), "http://10.0.0.5:9090"))
                .isEqualTo("http://10.0.0.5:9091/actuator/health");
    }

    @Test
    void actuatorHealthOnSharedPortHonoursContainerPrefix() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("server.servlet.context-path", "/app");
        assertThat(healthUrl(registrar(props(), env), "http://10.0.0.5:9090/app"))
                .isEqualTo("http://10.0.0.5:9090/app/actuator/health");
    }

}
