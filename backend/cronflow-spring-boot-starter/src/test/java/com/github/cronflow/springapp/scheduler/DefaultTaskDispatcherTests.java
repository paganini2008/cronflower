package com.github.cronflow.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronflow.springapp.CronflowSchedulerProperties;
import com.github.cronflow.springapp.scheduler.ExecutorRegistry.ExecutorInstance;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers the default task dispatcher: the no-executor failure, a successful round-trip and an
 * executor-reported failure against a tiny in-process executor that calls back, a timeout, the
 * completion-forwarding (multicast) path, the inbound-payload path and channel start. Never a
 * networked executor.
 */
class DefaultTaskDispatcherTests {

    private final ObjectMapper mapper = new ObjectMapper();

    private DefaultTaskDispatcher dispatcher(ExecutorRegistry registry, GossipCluster cluster,
            ObjectCodec codec) {
        return new DefaultTaskDispatcher(registry, cluster, codec,
                new CronflowSchedulerProperties().getDispatch());
    }

    private DispatchRequest request(long timeout) {
        return new DispatchRequest("app", "grp", "task", "com.X", "bean", "run", "p", timeout);
    }

    /** An executor server that, on each run POST, calls the dispatcher back with the given outcome. */
    private HttpServer callbackServer(DefaultTaskDispatcher dispatcher, boolean success, boolean reply)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/run", exchange -> {
            byte[] body;
            try (var in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            if (reply) {
                @SuppressWarnings("unchecked")
                Map<String, Object> run = mapper.readValue(new String(body, StandardCharsets.UTF_8),
                        Map.class);
                String executionId = (String) run.get("executionId");
                new Thread(() -> dispatcher.complete(new CompleteRequest(executionId, "grp", "task",
                        success, success ? "ok" : null, success ? null : "handler blew up", 0, 0, 1, 0,
                        "exec-repr"))).start();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private ExecutorRegistry registryWith(String runUrl) {
        ExecutorRegistry registry = new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN);
        registry.apply(new ExecutorInstance("app", "i1", runUrl, "health",
                System.currentTimeMillis(), 1));
        return registry;
    }

    private String runUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/run";
    }

    @Test
    void noLiveExecutorThrows() {
        DefaultTaskDispatcher dispatcher = dispatcher(
                new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN), mock(GossipCluster.class),
                mock(ObjectCodec.class));
        assertThatThrownBy(() -> dispatcher.dispatchAndWait(request(-1L)))
                .isInstanceOf(TaskInvocationException.class).hasMessageContaining("No live executor");
    }

    @Test
    void successfulDispatchReturnsValue() throws IOException {
        DefaultTaskDispatcher[] holder = new DefaultTaskDispatcher[1];
        ExecutorRegistry registry = new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN);
        GossipCluster cluster = mock(GossipCluster.class);
        DefaultTaskDispatcher dispatcher = dispatcher(registry, cluster, mock(ObjectCodec.class));
        holder[0] = dispatcher;
        HttpServer server = callbackServer(holder[0], true, true);
        try {
            registry.apply(new ExecutorInstance("app", "i1", runUrl(server), "health",
                    System.currentTimeMillis(), 1));
            assertThat(dispatcher.dispatchAndWait(request(5000L))).isEqualTo("ok");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void executorReportedFailureThrows() throws IOException {
        ExecutorRegistry registry = new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN);
        DefaultTaskDispatcher dispatcher =
                dispatcher(registry, mock(GossipCluster.class), mock(ObjectCodec.class));
        HttpServer server = callbackServer(dispatcher, false, true);
        try {
            registry.apply(new ExecutorInstance("app", "i1", runUrl(server), "health",
                    System.currentTimeMillis(), 1));
            assertThatThrownBy(() -> dispatcher.dispatchAndWait(request(5000L)))
                    .isInstanceOf(TaskInvocationException.class).hasMessageContaining("failure");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void timeoutWhenExecutorNeverReportsBack() throws IOException {
        ExecutorRegistry registry = new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN);
        DefaultTaskDispatcher dispatcher =
                dispatcher(registry, mock(GossipCluster.class), mock(ObjectCodec.class));
        HttpServer server = callbackServer(dispatcher, true, false); // accepts but never calls back
        try {
            registry.apply(new ExecutorInstance("app", "i1", runUrl(server), "health",
                    System.currentTimeMillis(), 1));
            assertThatThrownBy(() -> dispatcher.dispatchAndWait(request(300L)))
                    .isInstanceOf(TaskInvocationException.class).hasMessageContaining("did not report");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dispatchToUnreachableExecutorThrows() {
        ExecutorRegistry registry = registryWith("http://127.0.0.1:1/run"); // nothing listening
        DefaultTaskDispatcher dispatcher =
                dispatcher(registry, mock(GossipCluster.class), mock(ObjectCodec.class));
        assertThatThrownBy(() -> dispatcher.dispatchAndWait(request(1000L)))
                .isInstanceOf(TaskInvocationException.class);
    }

    @Test
    void completeNotFoundIsBroadcast() {
        GossipCluster cluster = mock(GossipCluster.class);
        ObjectCodec codec = mock(ObjectCodec.class);
        when(codec.encode(any())).thenReturn(new byte[] {1});
        DefaultTaskDispatcher dispatcher = dispatcher(
                new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN), cluster, codec);
        dispatcher.complete(new CompleteRequest("unknown", "grp", "task", true, "ok", null, 0, 0, 1, 0,
                "repr"));
        verify(cluster).multicastOn(eq(DefaultTaskDispatcher.CHANNEL), any(), any(), eq(false));
    }

    @Test
    void onPayloadForUnknownExecutionIsIgnored() {
        GossipCluster cluster = mock(GossipCluster.class);
        ObjectCodec codec = mock(ObjectCodec.class);
        when(codec.decode(any(), eq(CompleteRequest.class))).thenReturn(new CompleteRequest("x", "g",
                "t", true, "ok", null, 0, 0, 1, 0, "repr"));
        DefaultTaskDispatcher dispatcher = dispatcher(
                new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN), cluster, codec);
        dispatcher.onPayload(mock(Node.class), new byte[] {1}); // no pending -> no throw
    }

    @Test
    void startAddsListener() {
        GossipCluster cluster = mock(GossipCluster.class);
        DefaultTaskDispatcher dispatcher = dispatcher(
                new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN), cluster,
                mock(ObjectCodec.class));
        dispatcher.start();
        verify(cluster).addListener(eq(DefaultTaskDispatcher.CHANNEL), eq(dispatcher));
    }
}
