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
package com.github.cronflow.springapp.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import com.github.cronflow.springapp.ai.CronflowAiProperties.Provider;

/**
 * An {@link LlmProvider} over the OpenAI chat-completions API, which Ollama, vLLM, LocalAI and the
 * OpenAI cloud all speak. One driver, selected per configured provider. The base url and bearer token
 * come from configuration (sourced from environment variables), never hard-coded.
 */
public class OpenAiCompatibleProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
    private static final String OPENAI_CLOUD = "https://api.openai.com";

    private final String id;
    private final Provider config;
    private final boolean cloud;
    private final RestClient client;

    public OpenAiCompatibleProvider(String id, Provider config, RestClient.Builder builder) {
        this.id = id;
        this.config = config;
        this.cloud = "openai".equalsIgnoreCase(config.getKind()) || config.isAllowCloud();
        String base = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl()
                : OPENAI_CLOUD;
        RestClient.Builder b = builder.baseUrl(trimTrailingSlash(base));
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            b = b.defaultHeader("Authorization", "Bearer " + config.getApiKey());
        }
        this.client = b.build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String defaultModel() {
        return config.getDefaultModel();
    }

    @Override
    public boolean cloud() {
        return cloud;
    }

    @Override
    public java.util.List<String> models() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (config.getDefaultModel() != null && !config.getDefaultModel().isBlank()) {
            out.add(config.getDefaultModel());
        }
        if (config.getModels() != null && !config.getModels().isEmpty()) {
            out.addAll(config.getModels());
            return new java.util.ArrayList<>(out);
        }
        if (cloud) {
            // Curated current OpenAI chat models; override with cronflow.ai.providers.<id>.models.
            out.addAll(java.util.List.of("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1",
                    "o4-mini", "o3-mini"));
            return new java.util.ArrayList<>(out);
        }
        // A compatible server (Ollama/vLLM/LocalAI): ask it what it has installed.
        try {
            OllamaTags tags = client.get().uri("/api/tags").retrieve().body(OllamaTags.class);
            if (tags != null && tags.models() != null) {
                for (OllamaTag t : tags.models()) {
                    if (t != null && t.name() != null && !t.name().isBlank()) {
                        out.add(t.name());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("cronflow.ai: could not list models for '{}': {}", id, e.toString());
        }
        return new java.util.ArrayList<>(out);
    }

    /** The subset of Ollama's {@code /api/tags} response we read. */
    record OllamaTags(java.util.List<OllamaTag> models) {}

    record OllamaTag(String name) {}

    @Override
    public LlmResponse chat(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model() != null ? request.model() : config.getDefaultModel());
        body.put("messages", request.messages());
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            body.put("max_tokens", request.maxTokens());
        }
        if (request.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        try {
            ChatCompletion resp = client.post().uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .body(ChatCompletion.class);
            if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                return LlmResponse.failed("empty response from provider '" + id + "'");
            }
            ChatCompletion.Choice choice = resp.choices().get(0);
            String content = choice.message() == null ? "" : choice.message().content();
            int p = resp.usage() == null ? 0 : resp.usage().prompt_tokens();
            int c = resp.usage() == null ? 0 : resp.usage().completion_tokens();
            return LlmResponse.of(content, p, c, choice.finish_reason());
        } catch (Exception e) {
            log.warn("cronflow.ai: provider '{}' chat failed", id, e);
            return LlmResponse.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Minimal view of the chat-completions response. Field names match the API's snake_case. */
    public record ChatCompletion(List<Choice> choices, Usage usage) {
        public record Choice(Message message, String finish_reason) {}

        public record Message(String role, String content) {}

        public record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}
    }
}
