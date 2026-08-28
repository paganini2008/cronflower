package com.github.cronsmith.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real HTTP path of the WebClient-based client against a tiny in-process server: a
 * successful POST, and the failover that tries the next server URL when the first one errors.
 */
class WebClientCronsmithServerClientHttpTests {

    private HttpServer server(int status, AtomicInteger hits) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (hits != null) {
                hits.incrementAndGet();
            }
            // Drain the request body first: com.sun's server resets the connection otherwise.
            try (var in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            if (status == 200) {
                // register() reads the assigned instanceId from the body; other calls ignore it.
                byte[] body = "{\"instanceId\":\"i-test\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
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

    private CronsmithClientProperties propsFor(String... urls) {
        CronsmithClientProperties p = new CronsmithClientProperties();
        p.setServerUrls(List.of(urls));
        p.setConnectTimeoutMillis(1000);
        p.setReadTimeoutMillis(2000);
        return p;
    }

    @Test
    void postSucceedsAgainstAHealthyServer() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = server(200, hits);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            WebClientCronsmithServerClient client =
                    new WebClientCronsmithServerClient(propsFor(base));
            assertThat(client.complete(
                    new CompleteRequest("e", "g", "t", true, "ok", null, 1, 2, 1, 0, null))).isTrue();
            assertThat(hits.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsOverToTheNextServerUrl() throws IOException {
        AtomicInteger badHits = new AtomicInteger();
        AtomicInteger goodHits = new AtomicInteger();
        HttpServer bad = server(500, badHits);
        HttpServer good = server(200, goodHits);
        try {
            String badUrl = "http://127.0.0.1:" + bad.getAddress().getPort();
            String goodUrl = "http://127.0.0.1:" + good.getAddress().getPort();
            WebClientCronsmithServerClient client =
                    new WebClientCronsmithServerClient(propsFor(badUrl, goodUrl));
            String assignedId = client
                    .register(new RegistrationRequest("app", "i", "run", "health", List.of(), 1));
            assertThat(assignedId).isEqualTo("i-test");
            assertThat(badHits.get()).isEqualTo(1);
            assertThat(goodHits.get()).isEqualTo(1);
        } finally {
            bad.stop(0);
            good.stop(0);
        }
    }

    @Test
    void returnsFalseWhenEveryServerFails() throws IOException {
        HttpServer bad = server(503, null);
        try {
            String badUrl = "http://127.0.0.1:" + bad.getAddress().getPort();
            WebClientCronsmithServerClient client =
                    new WebClientCronsmithServerClient(propsFor(badUrl));
            assertThat(client.heartbeat(new HeartbeatRequest("app", "i", "run", "health", 1)))
                    .isFalse();
        } finally {
            bad.stop(0);
        }
    }

}
