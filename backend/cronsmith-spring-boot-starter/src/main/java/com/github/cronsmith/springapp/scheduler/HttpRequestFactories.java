package com.github.cronsmith.springapp.scheduler;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * One place to build the JDK-backed {@link SimpleClientHttpRequestFactory} that every server-side
 * {@code RestClient} uses (executor dispatch, executor health checks, and URL/API tasks), so the
 * choice of request factory and how its timeouts are set is not duplicated across call sites.
 *
 * @Description: HttpRequestFactories
 * @Author: Fred Feng
 * @Date: 28/08/2026
 * @Version 1.0.0
 */
final class HttpRequestFactories {

    private HttpRequestFactories() {}

    static SimpleClientHttpRequestFactory create(int connectTimeoutMillis, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMillis);
        factory.setReadTimeout(readTimeoutMillis);
        return factory;
    }
}
