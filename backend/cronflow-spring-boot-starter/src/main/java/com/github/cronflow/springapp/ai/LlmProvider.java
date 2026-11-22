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

/**
 * A chat model behind the OpenAI chat-completions shape. Drivers include a private Ollama endpoint, the
 * OpenAI cloud, and any compatible server (vLLM, LocalAI). One interface, many drivers.
 *
 * <p>
 * {@link #cloud()} tells the generation service whether this provider sends data off the local network,
 * so the service can redact before the call and honour a private-only request.
 */
public interface LlmProvider {

    /** Stable id, as configured, for example {@code ollama} or {@code openai}. */
    String id();

    /** The model used when a request does not name one. */
    String defaultModel();

    /** The models a caller may pick for this provider (the console's model dropdown). Defaults to just
     *  {@link #defaultModel()}; a provider that can enumerate its models overrides this. */
    default java.util.List<String> models() {
        return java.util.List.of(defaultModel());
    }

    /** True when this provider sends prompts to a third party off the local network. */
    boolean cloud();

    /** Run one chat turn. Never throws for a provider error; returns {@link LlmResponse#failed}. */
    LlmResponse chat(LlmRequest request);
}
