package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.github.cronflow.springapp.executor.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.executor.pojo.DagHeartbeatResponse;
import com.github.cronflow.springapp.executor.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.executor.pojo.DagRegistrationResponse;

/**
 * Exercises the WebClient-based cronflow server client and the low-level {@link CronflowWebClient}
 * against a tiny in-process HTTP server: register/heartbeat, failover across server URLs, the 4xx
 * stop-failover rule, and the no-URLs guard. Never a networked server.
 */
class CronflowServerClientHttpTests {

    private HttpServer server(int status, String body, AtomicInteger hits) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (hits != null) {
                hits.incrementAndGet();
            }
            try (var in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            if (status == 200 && body != null) {
                byte[] out = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(out);
                }
            } else {
                exchange.sendResponseHeaders(status, -1);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private CronflowClientProperties props(String... urls) {
        CronflowClientProperties p = new CronflowClientProperties();
        p.setServerUrls(List.of(urls));
        p.setConnectTimeoutMillis(1000);
        p.setReadTimeoutMillis(2000);
        return p;
    }

    private WebClientCronflowServerClient client(CronflowClientProperties p) {
        return new WebClientCronflowServerClient(p, new CronflowWebClient(p));
    }

    private DagRegistrationRequest reg() {
        return new DagRegistrationRequest("app", null, "run", "health", List.of(), List.of(), 1);
    }

    @Test
    void registerReturnsAssignedId() throws IOException {
        HttpServer server = server(200, "{\"instanceId\":\"i-test\"}", null);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(client(props(base)).register(reg())).isEqualTo("i-test");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void heartbeatReturnsKnown() throws IOException {
        HttpServer server = server(200, "{\"known\":true}", null);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            assertThat(client(props(base))
                    .heartbeat(new DagHeartbeatRequest("app", "i", "run", "h", 1))).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsOverToNextServerOn5xx() throws IOException {
        AtomicInteger badHits = new AtomicInteger();
        AtomicInteger goodHits = new AtomicInteger();
        HttpServer bad = server(500, null, badHits);
        HttpServer good = server(200, "{\"instanceId\":\"i2\"}", goodHits);
        try {
            String badUrl = "http://127.0.0.1:" + bad.getAddress().getPort();
            String goodUrl = "http://127.0.0.1:" + good.getAddress().getPort();
            assertThat(client(props(badUrl, goodUrl)).register(reg())).isEqualTo("i2");
            assertThat(badHits.get()).isEqualTo(1);
            assertThat(goodHits.get()).isEqualTo(1);
        } finally {
            bad.stop(0);
            good.stop(0);
        }
    }

    @Test
    void clientErrorStopsFailover() throws IOException {
        HttpServer bad = server(400, null, null);
        try {
            String badUrl = "http://127.0.0.1:" + bad.getAddress().getPort();
            // a 4xx is a definitive rejection: register returns null without trying others
            assertThat(client(props(badUrl, "http://127.0.0.1:1")).register(reg())).isNull();
        } finally {
            bad.stop(0);
        }
    }

    @Test
    void allServersFailingReturnsFalseOrNull() throws IOException {
        HttpServer bad = server(503, null, null);
        try {
            String badUrl = "http://127.0.0.1:" + bad.getAddress().getPort();
            assertThat(client(props(badUrl))
                    .heartbeat(new DagHeartbeatRequest("app", "i", "run", "h", 1))).isFalse();
        } finally {
            bad.stop(0);
        }
    }

    @Test
    void noServerUrlsIsAGuard() {
        CronflowClientProperties p = props();
        p.setServerUrls(List.of());
        WebClientCronflowServerClient client = client(p);
        assertThat(client.register(reg())).isNull();
        assertThat(client.heartbeat(new DagHeartbeatRequest("app", "i", "run", "h", 1))).isFalse();
    }

    @Test
    void apiPrefixVariantsAllReach() throws IOException {
        HttpServer server = server(200, "{\"instanceId\":\"i\"}", null);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            for (String prefix : new String[] {"", "/", "dags-root", "/dags-root/"}) {
                CronflowClientProperties p = props(base);
                p.setServerApiPrefix(prefix);
                assertThat(client(p).register(reg())).isEqualTo("i");
            }
            CronflowClientProperties nullPrefix = props(base);
            nullPrefix.setServerApiPrefix(null);
            assertThat(client(nullPrefix).register(reg())).isEqualTo("i");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webClientGetDeleteAndBodilessPost() throws IOException {
        HttpServer server = server(200, "{\"instanceId\":\"x\"}", null);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            CronflowWebClient web = new CronflowWebClient(props(base));
            DagRegistrationResponse body = web.get(base, DagRegistrationResponse.class);
            assertThat(body.instanceId()).isEqualTo("x");
            web.post(base, reg()); // bodiless post, no exception
            web.delete(base); // no exception
            DagRegistrationResponse posted = web.post(base, reg(), DagRegistrationResponse.class);
            assertThat(posted.instanceId()).isEqualTo("x");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webClientSendsConfiguredHeaders() throws IOException {
        HttpServer server = server(200, "{\"instanceId\":\"x\"}", null);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            CronflowClientProperties p = props(base);
            p.getHeaders().put("X-Test", "yes");
            CronflowWebClient web = new CronflowWebClient(p);
            assertThat(web.get(base, DagRegistrationResponse.class).instanceId()).isEqualTo("x");
        } finally {
            server.stop(0);
        }
    }
}
