package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers the branch logic of the task-executor server client not reached by the happy-path HTTP test:
 * the 4xx stop-failover rule, the no-URLs guard, and the API-prefix variants.
 */
class WebClientCronflowExecutorServerClientBranchTests {

    private HttpServer server(int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (var in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            if (status == 200) {
                byte[] body = "{\"instanceId\":\"i\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } else {
                exchange.sendResponseHeaders(status, -1);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private CronflowExecutorProperties props(String... urls) {
        CronflowExecutorProperties p = new CronflowExecutorProperties();
        p.setServerUrls(List.of(urls));
        p.setConnectTimeoutMillis(1000);
        p.setReadTimeoutMillis(2000);
        return p;
    }

    private RegistrationRequest reg() {
        return new RegistrationRequest("app", "i", "run", "health", List.of(), 1);
    }

    @Test
    void clientErrorStopsFailoverOnRegister() throws IOException {
        HttpServer bad = server(400);
        try {
            String badUrl = "http://127.0.0.1:" + bad.getAddress().getPort();
            assertThat(new WebClientCronflowExecutorServerClient(props(badUrl, "http://127.0.0.1:1"))
                    .register(reg())).isNull();
        } finally {
            bad.stop(0);
        }
    }

    @Test
    void clientErrorStopsFailoverOnPost() throws IOException {
        HttpServer bad = server(403);
        try {
            String badUrl = "http://127.0.0.1:" + bad.getAddress().getPort();
            assertThat(new WebClientCronflowExecutorServerClient(props(badUrl, "http://127.0.0.1:1"))
                    .complete(new CompleteRequest("e", "g", "t", true, "ok", null, 1, 2, 1, 0, null)))
                            .isFalse();
        } finally {
            bad.stop(0);
        }
    }

    @Test
    void noServerUrlsIsAGuard() {
        CronflowExecutorProperties p = props();
        p.setServerUrls(List.of());
        WebClientCronflowExecutorServerClient client = new WebClientCronflowExecutorServerClient(p);
        assertThat(client.register(reg())).isNull();
        assertThat(client.heartbeat(new HeartbeatRequest("app", "i", "run", "health", 1))).isFalse();
        assertThat(client.complete(new CompleteRequest("e", "g", "t", true, "ok", null, 1, 2, 1, 0,
                null))).isFalse();
    }

    @Test
    void apiPrefixVariantsReachTheServer() throws IOException {
        HttpServer good = server(200);
        try {
            String base = "http://127.0.0.1:" + good.getAddress().getPort();
            for (String prefix : new String[] {"", "/", "root", "/root/"}) {
                CronflowExecutorProperties p = props(base);
                p.setServerApiPrefix(prefix);
                assertThat(new WebClientCronflowExecutorServerClient(p).register(reg())).isEqualTo("i");
            }
            CronflowExecutorProperties nullPrefix = props(base);
            nullPrefix.setServerApiPrefix(null);
            assertThat(new WebClientCronflowExecutorServerClient(nullPrefix).register(reg()))
                    .isEqualTo("i");
        } finally {
            good.stop(0);
        }
    }

    @Test
    void heartbeatFailsOverThenSucceeds() throws IOException {
        HttpServer bad = server(500);
        HttpServer good = server(200);
        try {
            String badUrl = "http://127.0.0.1:" + bad.getAddress().getPort();
            String goodUrl = "http://127.0.0.1:" + good.getAddress().getPort();
            assertThat(new WebClientCronflowExecutorServerClient(props(badUrl, goodUrl))
                    .heartbeat(new HeartbeatRequest("app", "i", "run", "health", 1))).isTrue();
        } finally {
            bad.stop(0);
            good.stop(0);
        }
    }
}
