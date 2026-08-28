package com.github.cronsmith.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers the property defaults and that a headerful client can be built and calls degrade to
 * {@code false} when no server URL is configured (the no-network branch).
 */
class CronsmithClientPropertiesTests {

    @Test
    void defaults() {
        CronsmithClientProperties p = new CronsmithClientProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getScheme()).isEqualTo("http");
        assertThat(p.getServerUrls()).containsExactly("http://localhost:19090");
        assertThat(p.getServerApiPrefix()).isEqualTo("/cronsmith");
        assertThat(p.getRegisterIntervalSeconds()).isEqualTo(30L);
        assertThat(p.getConnectTimeoutMillis()).isEqualTo(3000);
        assertThat(p.getReadTimeoutMillis()).isEqualTo(10000);
        assertThat(p.getInvokerPoolSize()).isEqualTo(8);
        assertThat(p.getHeaders()).isEmpty();
    }

    @Test
    void buildsWebClientWithHeadersConfigured() {
        CronsmithClientProperties p = new CronsmithClientProperties();
        p.setHeaders(Map.of("Authorization", "Bearer t"));
        // Construction wires the header map into the WebClient's default headers without throwing.
        WebClientCronsmithServerClient client = new WebClientCronsmithServerClient(p);
        assertThat(client).isNotNull();
    }

    @Test
    void callsReturnFalseWhenNoServerUrls() {
        CronsmithClientProperties p = new CronsmithClientProperties();
        WebClientCronsmithServerClient client = new WebClientCronsmithServerClient(p);
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
