package com.github.cronsmith.springapp.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A plain bean — deliberately NOT annotated with {@code @Task}. It exists to show the other entry
 * point: a task created through the server's CRUD API can target any executor bean method, not just
 * an auto-discovered @Task. Here the method fetches a URL, i.e. a "URL task".
 */
@Component
public class UrlFetcher {

    private static final Logger log = LoggerFactory.getLogger(UrlFetcher.class);

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("[urlFetcher] GET {} -> HTTP {}", url, response.statusCode());
        return "GET " + url + " -> HTTP " + response.statusCode();
    }

}
