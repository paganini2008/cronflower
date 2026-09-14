package com.github.cronflow.springapp.executor;

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
 * {@link CronflowServerClient} over HTTP using {@link WebClient} on the JDK HttpClient connector.
 * Several server URLs may be configured; they are tried in turn until one accepts (a 4xx stops
 * failover — every node would answer the same). Structurally identical to cronsmith's client.
 *
 * @Description: WebClientCronflowServerClient
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class WebClientCronflowServerClient implements CronflowServerClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientCronflowServerClient.class);

    private final CronflowClientProperties properties;
    private final WebClient webClient;

    public WebClientCronflowServerClient(CronflowClientProperties properties) {
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
    public String register(DagRegistrationRequest request) {
        String path = apiPath(REGISTER_SUBPATH);
        List<String> urls = properties.getServerUrls();
        if (urls == null || urls.isEmpty()) {
            log.warn("No cronflow.client.server-urls configured; cannot POST {}", path);
            return null;
        }
        RuntimeException last = null;
        for (String base : urls) {
            String url = trimTrailingSlash(base) + path;
            try {
                DagRegistrationResponse response = webClient.post().uri(url)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(request).retrieve()
                        .bodyToMono(DagRegistrationResponse.class)
                        .block(Duration.ofMillis(properties.getReadTimeoutMillis()));
                return response != null ? response.instanceId() : null;
            } catch (WebClientResponseException e) {
                last = e;
                if (e.getStatusCode().is4xxClientError()) {
                    log.warn("POST {} rejected with {}; not trying other servers", url, e.getStatusCode());
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
    public boolean heartbeat(DagHeartbeatRequest request) {
        return post(apiPath(HEARTBEAT_SUBPATH), request);
    }

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
            log.warn("No cronflow.client.server-urls configured; cannot POST {}", path);
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
                    log.warn("POST {} rejected with {}; not trying other servers", url, e.getStatusCode());
                    return false;
                }
                log.debug("POST {} failed with {}: {}", url, e.getStatusCode(), e.toString());
            } catch (RuntimeException e) {
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
