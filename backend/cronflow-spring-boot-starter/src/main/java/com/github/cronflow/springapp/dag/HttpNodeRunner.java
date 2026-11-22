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

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import com.github.cronflow.springapp.pojo.DagDefinition.HttpEndpointDef;
import com.github.cronflow.springapp.pojo.DagDefinition.IoMappingDef;
import com.github.cronflow.springapp.pojo.DagDefinition.NodeDef;
import com.github.cronflow.springapp.pojo.NodeRunResult;

/**
 * Runs an HTTP node — a DAG node whose body is a call to an external endpoint — on the scheduler
 * itself, with no executor involved. Once a DAG is triggered it calls each HTTP node's endpoint
 * according to the graph; the service behind the endpoint can be written in anything (Python, Go,
 * Rust, ...), because the only contract is HTTP + JSON.
 *
 * <p>
 * Channel wiring (the node's {@link IoMappingDef}): without a mapping, the merged channel state is
 * sent as the JSON request body (for POST/PUT/PATCH) and the JSON response object's keys are taken as
 * the channel updates. With a mapping, {@code input} reads named channels into request fields and
 * {@code output} writes response fields (by dotted path) into named channels.
 *
 * @Description: HttpNodeRunner
 * @Author: Fred Feng
 * @Date: 22/11/2026
 * @Version 1.0.0
 */
public class HttpNodeRunner {

    private static final Logger log = LoggerFactory.getLogger(HttpNodeRunner.class);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public HttpNodeRunner(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Call {@code node}'s endpoint with the current channel {@code state}; return the channel updates. */
    public NodeRunResult run(NodeDef node, Map<String, Object> state) {
        HttpEndpointDef endpoint = node.endpoint();
        if (endpoint == null || endpoint.url() == null || endpoint.url().isBlank()) {
            return new NodeRunResult(false, Map.of(),
                    "http node '" + node.name() + "' has no endpoint url");
        }
        String method = endpoint.method() == null || endpoint.method().isBlank() ? "POST"
                : endpoint.method().trim().toUpperCase();
        boolean bodyless = "GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method);
        Map<String, Object> safeState = state == null ? Map.of() : state;
        try {
            RestClient.RequestBodySpec spec =
                    restClient.method(HttpMethod.valueOf(method)).uri(endpoint.url());
            if (endpoint.headers() != null) {
                endpoint.headers().forEach(spec::header);
            }
            Map<String, Object> response;
            if (bodyless) {
                response = spec.retrieve().body(MAP_TYPE);
            } else {
                response = spec.contentType(MediaType.APPLICATION_JSON)
                        .body(buildRequest(node.io(), safeState)).retrieve().body(MAP_TYPE);
            }
            Map<String, Object> updates = mapResponse(node, response);
            log.info("cronflow: ran http node {} -> {} {}", node.name(), method, endpoint.url());
            return new NodeRunResult(true, updates, null);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("cronflow: http node {} ({} {}) failed: {}", node.name(), method, endpoint.url(),
                    cause.toString());
            return new NodeRunResult(false, Map.of(), cause.toString());
        }
    }

    /** Build the request body: the whole state by default, or a {field -> channel value} map. */
    private static Object buildRequest(IoMappingDef io, Map<String, Object> state) {
        if (io == null || io.input() == null || io.input().isEmpty()) {
            return state;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        io.input().forEach((field, channel) -> body.put(field, state.get(channel)));
        return body;
    }

    /** Map the response to channel updates: the response object by default, or {channel -> path}. */
    private static Map<String, Object> mapResponse(NodeDef node, Map<String, Object> response) {
        IoMappingDef io = node.io();
        if (io == null || io.output() == null || io.output().isEmpty()) {
            if (response == null) {
                return Map.of();
            }
            return response;
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        io.output().forEach((channel, path) -> updates.put(channel, extract(response, path)));
        return updates;
    }

    /** Read a dotted path (e.g. {@code data.score}) from a nested JSON response; null if absent. */
    private static Object extract(Map<String, Object> response, String path) {
        if (response == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = response;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

}
