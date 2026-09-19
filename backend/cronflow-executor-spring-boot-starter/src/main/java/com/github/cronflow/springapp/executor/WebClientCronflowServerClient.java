package com.github.cronflow.springapp.executor;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.github.cronflow.springapp.executor.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.executor.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.executor.pojo.DagRegistrationResponse;

/**
 * {@link CronflowServerClient} over HTTP: the actual calls go through the injected
 * {@link CronflowWebClient} wrapper; this class only owns the domain logic — building the API path and
 * failing over across the configured server URLs (a 4xx stops failover, since every node would answer
 * the same). Structurally identical to cronsmith's client.
 *
 * @Description: WebClientCronflowServerClient
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class WebClientCronflowServerClient implements CronflowServerClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientCronflowServerClient.class);

    private final CronflowClientProperties properties;
    private final CronflowWebClient http;

    public WebClientCronflowServerClient(CronflowClientProperties properties, CronflowWebClient http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public String register(DagRegistrationRequest request) {
        DagRegistrationResponse response = overServers(apiPath(REGISTER_SUBPATH),
                url -> http.post(url, request, DagRegistrationResponse.class), null);
        return response != null ? response.instanceId() : null;
    }

    @Override
    public boolean heartbeat(DagHeartbeatRequest request) {
        Boolean ok = overServers(apiPath(HEARTBEAT_SUBPATH), url -> {
            http.post(url, request);
            return Boolean.TRUE;
        }, Boolean.FALSE);
        return Boolean.TRUE.equals(ok);
    }

    /** Try {@code call} against each configured server URL + path until one succeeds; a 4xx stops
     *  failover. Returns {@code onFailure} if none succeeds. */
    private <T> T overServers(String path, java.util.function.Function<String, T> call, T onFailure) {
        List<String> urls = properties.getServerUrls();
        if (urls == null || urls.isEmpty()) {
            log.warn("No cronflow.client.server-urls configured; cannot call {}", path);
            return onFailure;
        }
        RuntimeException last = null;
        for (String base : urls) {
            String url = trimTrailingSlash(base) + path;
            try {
                return call.apply(url);
            } catch (WebClientResponseException e) {
                last = e;
                if (e.getStatusCode().is4xxClientError()) {
                    log.warn("{} rejected with {}; not trying other servers", url, e.getStatusCode());
                    return onFailure;
                }
                log.debug("{} failed with {}: {}", url, e.getStatusCode(), e.toString());
            } catch (RuntimeException e) {
                last = e;
                log.debug("{} failed: {}", url, e.toString());
            }
        }
        log.warn("All configured servers failed for {}{}", path, last == null ? "" : ": " + last);
        return onFailure;
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

    private static String trimTrailingSlash(String base) {
        String s = base.trim();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

}
