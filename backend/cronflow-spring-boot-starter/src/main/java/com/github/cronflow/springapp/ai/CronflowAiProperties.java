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
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Configuration for AI DAG generation, under {@code cronflow.ai}. Private-first: the default provider is
 * the private endpoint, and a cloud provider must set {@code allow-cloud=true} to be usable. Secrets
 * ({@code apiKey}, {@code baseUrl}) come from environment variables referenced in the yaml, never stored
 * here literally.
 */
@Data
@ConfigurationProperties("cronflow.ai")
public class CronflowAiProperties {

    /** Master switch. When false, no provider or generator bean is created. */
    private boolean enabled = false;

    /** Id of the provider used when a request does not name one. */
    private String defaultProvider = "ollama";

    /** Low temperature by default: generation should be stable, not creative. */
    private double temperature = 0.1;

    /** Configured providers, keyed by id. */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    private Embeddings embeddings = new Embeddings();
    private Rag rag = new Rag();
    private Budget budget = new Budget();
    private Redaction redaction = new Redaction();

    @Data
    public static class Provider {
        /** openai | compatible. {@code compatible} covers Ollama, vLLM, LocalAI. */
        private String kind = "compatible";
        /** Base url of the OpenAI-compatible server. Omit for the real OpenAI cloud. */
        private String baseUrl;
        /** Bearer token, from an environment variable. */
        private String apiKey;
        /** Model used when the request does not name one. */
        private String defaultModel;
        /** Models offered in the console's model dropdown. Empty = the provider enumerates them
         *  (a compatible server via {@code /api/tags}, or a curated list for OpenAI). */
        private java.util.List<String> models = new java.util.ArrayList<>();
        /** True marks this provider as sending data off-network; it is then only used when allowed. */
        private boolean allowCloud = false;
    }

    @Data
    public static class Embeddings {
        private String provider = "ollama";
        private String model = "nomic-embed-text";
    }

    @Data
    public static class Rag {
        private boolean enabled = false;
        private int topK = 8;
    }

    @Data
    public static class Budget {
        private int maxPromptTokens = 8000;
        private int maxCompletionTokens = 4000;
        private int maxRepairs = 2;
    }

    @Data
    public static class Redaction {
        private boolean enabled = true;
    }
}
