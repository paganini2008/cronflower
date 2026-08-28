package com.github.cronsmith.springapp.executor;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * {@link CronsmithServerClient} over HTTP using {@link WebClient}.
 *
 * <p>
 * Several server URLs may be configured; they are tried in turn until one accepts the call. Which
 * node answers does not matter because every write is routed to the leader on the server side. The
 * JDK HttpClient connector is used, so no reactor-netty is required.
 *
 * @Description: WebClientCronsmithServerClient
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class WebClientCronsmithServerClient implements CronsmithServerClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientCronsmithServerClient.class);

    private final CronsmithClientProperties properties;
    private final WebClient webClient;

    public WebClientCronsmithServerClient(CronsmithClientProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis())).build();
        this.webClient = WebClient.builder().clientConnector(new JdkClientHttpConnector(httpClient))
                .defaultHeaders(headers -> {
                    if (properties.getHeaders() != null) {
                        properties.getHeaders().forEach(headers::add);
                    }
                }).build();
    }

    @Override
    public String register(RegistrationRequest request) {
        String path = apiPath(REGISTER_SUBPATH);
        List<String> urls = properties.getServerUrls();
        if (urls == null || urls.isEmpty()) {
            log.warn("No cronsmith.client.server-urls configured; cannot POST {}", path);
            return null;
        }
        RuntimeException last = null;
        for (String base : urls) {
            String url = trimTrailingSlash(base) + path;
            try {
                RegistrationResponse response = webClient.post().uri(url)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(request).retrieve()
                        .bodyToMono(RegistrationResponse.class)
                        .block(Duration.ofMillis(properties.getReadTimeoutMillis()));
                return response != null ? response.instanceId() : null;
            } catch (WebClientResponseException e) {
                last = e;
                if (e.getStatusCode().is4xxClientError()) {
                    log.warn("POST {} rejected with {}; not trying other servers", url,
                            e.getStatusCode());
                    return null;
                }
                log.debug("POST {} failed with {}: {}", url, e.getStatusCode(), e.toString());
            } catch (RuntimeException e) {
                last = e;
                log.debug("POST {} failed: {}", url, e.toString());
            }
        }
        log.warn("All configured servers failed for {}{}", path, last == null ? "" : ": " + last);
        return null;
    }

    @Override
    public boolean heartbeat(HeartbeatRequest request) {
        return post(apiPath(HEARTBEAT_SUBPATH), request);
    }

    @Override
    public boolean complete(CompleteRequest request) {
        return post(apiPath(COMPLETE_SUBPATH), request);
    }

    /** Prepend the configured server API prefix (default /cronsmith) to a sub-path. */
    private String apiPath(String subpath) {
        String prefix = properties.getServerApiPrefix();
        if (prefix == null) {
            return subpath;
        }
        prefix = prefix.trim();
        if (prefix.isEmpty() || prefix.equals("/")) {
            return subpath;
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + subpath;
    }

    private boolean post(String path, Object body) {
        List<String> urls = properties.getServerUrls();
        if (urls == null || urls.isEmpty()) {
            log.warn("No cronsmith.client.server-urls configured; cannot POST {}", path);
            return false;
        }
        RuntimeException last = null;
        for (String base : urls) {
            String url = trimTrailingSlash(base) + path;
            try {
                webClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                        .retrieve().toBodilessEntity()
                        .block(Duration.ofMillis(properties.getReadTimeoutMillis()));
                return true;
            } catch (WebClientResponseException e) {
                last = e;
                if (e.getStatusCode().is4xxClientError()) {
                    // A 4xx means the request itself was rejected (bad body, auth, wrong path).
                    // Every node would answer the same way, so failing over only masks it — stop
                    // and surface the status instead.
                    log.warn("POST {} rejected with {}; not trying other servers", url,
                            e.getStatusCode());
                    return false;
                }
                // 5xx: this node is unhealthy, try the next one.
                log.debug("POST {} failed with {}: {}", url, e.getStatusCode(), e.toString());
            } catch (RuntimeException e) {
                // Connection refused, timeout, etc. — try the next server.
                last = e;
                log.debug("POST {} failed: {}", url, e.toString());
            }
        }
        log.warn("All configured servers failed for {}{}", path, last == null ? "" : ": " + last);
        return false;
    }

    private static String trimTrailingSlash(String base) {
        String s = base.trim();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

}
