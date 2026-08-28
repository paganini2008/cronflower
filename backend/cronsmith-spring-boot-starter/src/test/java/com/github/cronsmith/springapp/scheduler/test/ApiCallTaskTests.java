package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.ApiCallTask;
import com.github.cronsmith.springapp.scheduler.DefaultTaskFactory;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskInvocationException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 *
 * Drives an {@link ApiCallTask} against a mock HTTP server. The task's endpoint and verb are not
 * stored separately but packed into an HTTP request line in the {@code url} column
 * ({@code "GET http://host/x HTTP/1.1"}); these tests exercise that the line is parsed back into a
 * method and an endpoint, and that the request goes out end to end.
 *
 * @Description: ApiCallTaskTests
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public class ApiCallTaskTests {

    private MockWebServer server;

    @BeforeEach
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private ApiCallTask task(String requestLine) {
        Map<String, Object> record = new HashMap<>();
        record.put("taskGroup", "http");
        record.put("taskName", "ping");
        record.put("cron", "0 0 12 * * ?");
        record.put("url", requestLine);
        return (ApiCallTask) new DefaultTaskFactory().createApiCallTask(record);
    }

    @Test
    public void testRequestLineIsParsed() {
        ApiCallTask task = task("POST https://example.com/create HTTP/1.1");
        assertEquals(TaskId.of("http", "ping"), task.getTaskId());
        assertEquals("POST", task.getHttpMethod());
        assertEquals("https://example.com/create", task.getEndpoint());
    }

    @Test
    public void testRequestLineWithoutVersionStillParses() {
        ApiCallTask task = task("GET https://example.com/x");
        assertEquals("GET", task.getHttpMethod());
        assertEquals("https://example.com/x", task.getEndpoint());
    }

    @Test
    public void testGetRequest() throws Exception {
        server.enqueue(new MockResponse().setBody("pong"));
        ApiCallTask task = task("GET " + server.url("/ping") + " HTTP/1.1");

        Object result = task.execute(null);
        assertEquals("pong", result);
        RecordedRequest recorded = server.takeRequest();
        assertEquals("GET", recorded.getMethod());
        assertEquals("/ping", recorded.getPath());
        assertEquals(0, recorded.getBodySize(), "a GET carries no body");
    }

    @Test
    public void testPostSendsJsonPayload() throws Exception {
        server.enqueue(new MockResponse().setBody("created"));
        ApiCallTask task = task("POST " + server.url("/create") + " HTTP/1.1");

        assertEquals("created", task.execute("{\"k\":\"v\"}"));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("{\"k\":\"v\"}", recorded.getBody().readUtf8());
        assertTrue(recorded.getHeader("Content-Type").contains("application/json"));
    }

    @Test
    public void testPutSendsPayload() throws Exception {
        server.enqueue(new MockResponse().setBody("updated"));
        ApiCallTask task = task("PUT " + server.url("/x") + " HTTP/1.1");

        assertEquals("updated", task.execute("body"));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("PUT", recorded.getMethod());
        assertEquals("body", recorded.getBody().readUtf8());
    }

    @Test
    public void testNon2xxResponseFails() {
        org.junit.jupiter.api.Assertions.assertThrows(TaskInvocationException.class, () -> {
        server.enqueue(new MockResponse().setResponseCode(500));
        task("GET " + server.url("/boom") + " HTTP/1.1").execute(null);
    
        });
    }

    @Test
    public void testAgainstPublicApi() {
        // A real call to a public API, so the task is seen working outside the mock. Skipped when
        // there is no network, so an offline build stays green.
        assumePublicNetwork();
        ApiCallTask task = task("GET https://httpbin.org/get HTTP/1.1");
        Object result = task.execute(null);
        assertNotNull(result);
        assertTrue(result.toString().contains("httpbin.org/get"));
    }

    @Test
    public void testPostJsonAgainstPublicApi() {
        assumePublicNetwork();
        ApiCallTask task = task("POST https://httpbin.org/post HTTP/1.1");
        Object result = task.execute("{\"job\":\"cronsmith\"}");
        assertNotNull(result);
        assertTrue(result.toString().contains("cronsmith"));
    }

    private static void assumePublicNetwork() {
        try {
            java.net.InetAddress.getByName("httpbin.org");
            java.net.HttpURLConnection connection =
                    (java.net.HttpURLConnection) java.net.URI.create("https://httpbin.org/status/200")
                            .toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            org.junit.Assume.assumeTrue("httpbin.org unreachable",
                    connection.getResponseCode() == 200);
            connection.disconnect();
        } catch (Exception e) {
            org.junit.Assume.assumeNoException("no public network", e);
        }
    }

}
