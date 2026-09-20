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
package com.github.cronflow.springapp.executor;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * A thin, injectable wrapper over {@link WebClient} with the HTTP verbs cronflow needs pre-defined
 * ({@link #post}, {@link #get}, {@link #delete}) — JSON in/out, a bounded blocking read, the configured
 * connect timeout and default headers. It is single-URL and low-level: callers pass a full URL and
 * keep any multi-server failover of their own. Errors propagate as
 * {@code WebClientResponseException} (HTTP status) or other runtime exceptions (network), so callers
 * can distinguish 4xx from a transient failure.
 *
 * @Description: CronflowWebClient
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class CronflowWebClient {

    private final WebClient webClient;
    private final long readTimeoutMillis;

    public CronflowWebClient(CronflowClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis())).build();
        this.webClient = WebClient.builder().clientConnector(new JdkClientHttpConnector(httpClient))
                .defaultHeaders(headers -> {
                    if (properties.getHeaders() != null) {
                        properties.getHeaders().forEach(headers::add);
                    }
                }).build();
        this.readTimeoutMillis = properties.getReadTimeoutMillis();
    }

    /** POST a JSON body and deserialize the response; returns null when the response has no body. */
    public <T> T post(String url, Object body, Class<T> responseType) {
        return webClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .retrieve().bodyToMono(responseType).block(readTimeout());
    }

    /** POST a JSON body, ignoring any response body. */
    public void post(String url, Object body) {
        webClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve()
                .toBodilessEntity().block(readTimeout());
    }

    /** GET and deserialize the response. */
    public <T> T get(String url, Class<T> responseType) {
        return webClient.get().uri(url).retrieve().bodyToMono(responseType).block(readTimeout());
    }

    /** DELETE, ignoring any response body. */
    public void delete(String url) {
        webClient.delete().uri(url).retrieve().toBodilessEntity().block(readTimeout());
    }

    private Duration readTimeout() {
        return Duration.ofMillis(readTimeoutMillis);
    }

}
