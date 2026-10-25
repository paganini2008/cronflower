package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import com.github.cronflow.springapp.dag.DagExecutorRegistry.ExecutorInstance;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.NodeRunResult;

/**
 * Covers the HTTP node dispatcher: the happy path against a tiny in-process server, the empty-response
 * fallback, and the no-live-executor failure. Never touches a real executor.
 */
class DagNodeDispatcherTests {

    private HttpServer server(String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/run", exchange -> {
            try (var in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                try (var os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private DagExecutorRegistry registryReturning(String runUrl) {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.pick("g")).thenReturn(Optional.of(
                new ExecutorInstance("app", "i1", runUrl, "health", 1, java.time.Instant.now())));
        return registry;
    }

    private DagDefinition.NodeDef node() {
        return new DagDefinition.NodeDef("n1", false, "ALL", 0, "bean", "method", "");
    }

    @Test
    void dispatchReturnsResultAndExecutor() throws IOException {
        HttpServer server = server("{\"ok\":true,\"updates\":{\"out\":1},\"errorDetail\":null}");
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/run";
            DagNodeDispatcher dispatcher =
                    new DagNodeDispatcher(registryReturning(url), RestClient.create());
            DagNodeDispatcher.Dispatched dispatched = dispatcher.dispatch("g", node(), Map.of("in", 1));
            assertThat(dispatched.result().ok()).isTrue();
            assertThat(dispatched.result().updates()).containsEntry("out", 1);
            assertThat(dispatched.executor()).isEqualTo(url);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emptyResponseBecomesAFailureResult() throws IOException {
        HttpServer server = server("");
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/run";
            DagNodeDispatcher dispatcher =
                    new DagNodeDispatcher(registryReturning(url), RestClient.create());
            DagNodeDispatcher.Dispatched dispatched = dispatcher.dispatch("g", node(), Map.of());
            assertThat(dispatched.result().ok()).isFalse();
            assertThat(dispatched.result().errorDetail()).isEqualTo("empty response");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void noLiveExecutorThrows() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.pick("g")).thenReturn(Optional.empty());
        DagNodeDispatcher dispatcher = new DagNodeDispatcher(registry, RestClient.create());
        assertThatThrownBy(() -> dispatcher.dispatch("g", node(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No live executor");
    }

    @Test
    void dispatchedRecordAccessors() {
        NodeRunResult result = new NodeRunResult(true, Map.of(), null);
        DagNodeDispatcher.Dispatched d = new DagNodeDispatcher.Dispatched(result, "url");
        assertThat(d.result()).isSameAs(result);
        assertThat(d.executor()).isEqualTo("url");
    }
}
