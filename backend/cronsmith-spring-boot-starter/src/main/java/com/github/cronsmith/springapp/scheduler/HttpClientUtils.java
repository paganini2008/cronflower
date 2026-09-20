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
package com.github.cronsmith.springapp.scheduler;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client for tasks whose work is an outbound call to an endpoint (the URL / API task). Built on
 * Spring's {@link RestClient} — the same client the dispatcher uses to reach executors — so the server
 * needs no extra HTTP library. The client is created once and reused.
 *
 * @Description: HttpClientUtils
 * @Author: Fred Feng
 * @Date: 28/08/2026
 * @Version 1.0.0
 */
public abstract class HttpClientUtils {

    private static final RestClient CLIENT =
            RestClient.builder().requestFactory(HttpRequestFactories.create(10_000, 60_000)).build();

    /**
     * Sends a request and returns the response body.
     *
     * @param dataType one of {@code json}, {@code xml}, {@code form}; anything else is sent as plain
     *        text
     * @throws IOException if the call fails or the response status is not successful
     */
    public static String sendRequest(String url, String httpMethod, Map<String, String> httpHeaders,
            String dataType, String data) throws IOException {
        if (httpMethod == null) {
            throw new IllegalArgumentException("HTTP method is required");
        }
        String method = httpMethod.toUpperCase();
        boolean hasBody = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        if (!hasBody && !"GET".equals(method)) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        }
        try {
            RestClient.RequestBodySpec spec = CLIENT.method(HttpMethod.valueOf(method)).uri(url)
                    .headers(headers -> {
                        if (httpHeaders != null) {
                            httpHeaders.forEach(headers::add);
                        }
                    });
            if (hasBody) {
                return spec.contentType(mediaType(dataType)).body(data != null ? data : "")
                        .retrieve().body(String.class);
            }
            return spec.retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw new IOException("Unexpected HTTP code: " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static MediaType mediaType(String dataType) {
        if ("json".equalsIgnoreCase(dataType)) {
            return MediaType.APPLICATION_JSON;
        }
        if ("xml".equalsIgnoreCase(dataType)) {
            return MediaType.APPLICATION_XML;
        }
        if ("form".equalsIgnoreCase(dataType)) {
            return MediaType.APPLICATION_FORM_URLENCODED;
        }
        return MediaType.TEXT_PLAIN;
    }

}
