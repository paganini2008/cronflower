/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cronflow.springapp.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import com.github.cronflow.springapp.pojo.DagDefinition.HttpEndpointDef;
import com.github.cronflow.springapp.pojo.DagDefinition.IoMappingDef;
import com.github.cronflow.springapp.pojo.DagDefinition.NodeDef;
import com.github.cronflow.springapp.pojo.NodeRunResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The HTTP node runner against a stub HTTP service, proving the "upstream endpoint's output becomes the
 * downstream endpoint's input" chaining — the same channel in/out a bean or script node has, here over
 * plain HTTP to a service that could be written in anything. No JSON library on the test classpath, so
 * the stub reads/writes JSON by hand; RestClient does the real (de)serialization under test.
 */
class HttpNodeRunnerTests {

    private HttpServer server;
    private String base;
    private HttpNodeRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // /score: returns {orderId, score}. orderId is echoed (the request body carried it).
        server.createContext("/score", exchange -> {
            String body = body(exchange);
            String orderId = body.contains("\"orderId\"") ? "o1" : "";
            writeJson(exchange, "{\"orderId\":\"" + orderId + "\",\"score\":42}");
        });
        // /decide: review when the request body carries score 42 (i.e. the upstream output arrived).
        server.createContext("/decide", exchange -> {
            String body = body(exchange);
            boolean highScore = body.replaceAll("\\s", "").contains("\"score\":42");
            writeJson(exchange, "{\"decision\":\"" + (highScore ? "review" : "ok") + "\"}");
        });
        // /nested: a nested object, for dotted-path output mapping.
        server.createContext("/nested", exchange -> {
            body(exchange);
            writeJson(exchange, "{\"data\":{\"risk\":7}}");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        runner = new HttpNodeRunner(RestClient.create());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void defaultMappingSendsStateAndTakesResponseObjectAsChannelUpdates() {
        NodeRunResult r = runner.run(httpNode("score", base + "/score", null), Map.of("orderId", "o1"));
        assertTrue(r.ok(), () -> "failed: " + r.errorDetail());
        assertEquals("o1", r.updates().get("orderId"));
        assertEquals(42, ((Number) r.updates().get("score")).intValue());
    }

    @Test
    void upstreamOutputBecomesDownstreamInput() {
        NodeRunResult a = runner.run(httpNode("score", base + "/score", null), Map.of("orderId", "o1"));
        assertTrue(a.ok(), () -> "A failed: " + a.errorDetail());
        // Channel state after A = inbound state merged with A's updates (what the engine does).
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("orderId", "o1");
        state.putAll(a.updates());
        // B decides from the score A produced — proving A's output fed B's input.
        NodeRunResult b = runner.run(httpNode("decide", base + "/decide", null), state);
        assertTrue(b.ok(), () -> "B failed: " + b.errorDetail());
        assertEquals("review", b.updates().get("decision"));
    }

    @Test
    void explicitOutputMappingReadsDottedPath() {
        IoMappingDef io = new IoMappingDef(null, Map.of("riskScore", "data.risk"));
        NodeRunResult r = runner.run(httpNode("nested", base + "/nested", io), Map.of());
        assertTrue(r.ok(), () -> "failed: " + r.errorDetail());
        assertEquals(7, ((Number) r.updates().get("riskScore")).intValue());
    }

    @Test
    void missingEndpointFailsCleanly() {
        NodeDef bad = new NodeDef("x", true, "ALL", 0, null, null, "", null, "http", null, null, null,
                null, null);
        assertFalse(runner.run(bad, Map.of()).ok());
    }

    private static NodeDef httpNode(String name, String url, IoMappingDef io) {
        return new NodeDef(name, true, "ALL", 0, null, null, "", null, "http",
                new HttpEndpointDef(url, "POST", null), io, null, null, null);
    }

    private static String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, out.length);
        exchange.getResponseBody().write(out);
        exchange.close();
    }
}
