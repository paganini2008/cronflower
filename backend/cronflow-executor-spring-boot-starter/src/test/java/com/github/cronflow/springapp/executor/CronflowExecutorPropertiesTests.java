package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers the property defaults and that a headerful client can be built and calls degrade to
 * {@code false} when no server URL is configured (the no-network branch).
 */
class CronflowExecutorPropertiesTests {

    @Test
    void defaults() {
        CronflowExecutorProperties p = new CronflowExecutorProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getScheme()).isEqualTo("http");
        assertThat(p.getServerUrls()).containsExactly("http://localhost:19090");
        assertThat(p.getServerApiPrefix()).isEqualTo("/cronflow/scheduler");
        assertThat(p.getRegisterIntervalSeconds()).isEqualTo(30L);
        assertThat(p.getConnectTimeoutMillis()).isEqualTo(3000);
        assertThat(p.getReadTimeoutMillis()).isEqualTo(10000);
        assertThat(p.getInvokerPoolSize()).isEqualTo(8);
        assertThat(p.getHeaders()).isEmpty();
    }

    @Test
    void buildsWebClientWithHeadersConfigured() {
        CronflowExecutorProperties p = new CronflowExecutorProperties();
        p.setHeaders(Map.of("Authorization", "Bearer t"));
        // Construction wires the header map into the WebClient's default headers without throwing.
        WebClientCronflowExecutorServerClient client = new WebClientCronflowExecutorServerClient(p);
        assertThat(client).isNotNull();
    }

    @Test
    void callsReturnFalseWhenNoServerUrls() {
        CronflowExecutorProperties p = new CronflowExecutorProperties();
        p.setServerUrls(java.util.List.of()); // no server configured, deterministic regardless of env
        WebClientCronflowExecutorServerClient client = new WebClientCronflowExecutorServerClient(p);
        assertThat(client.register(
                new RegistrationRequest("app", "i", "run", "health", java.util.List.of(), 1)))
                .isNull();
        assertThat(client.heartbeat(new HeartbeatRequest("app", "i", "run", "health", 1)))
                .isFalse();
        assertThat(client
                .complete(new CompleteRequest("e", "g", "t", true, null, null, 0, 0, 0, 0, null)))
                .isFalse();
    }

}
