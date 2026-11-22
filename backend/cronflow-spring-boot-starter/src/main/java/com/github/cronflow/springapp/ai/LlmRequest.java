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

import java.util.List;

/**
 * A provider-neutral chat request. {@code jsonMode} asks the provider for a JSON-only response (native
 * structured output where the driver supports it, otherwise enforced by the system prompt plus a parse).
 * {@code model} null means the provider's configured default model.
 */
public record LlmRequest(String model, List<LlmMessage> messages, Double temperature, boolean jsonMode,
        Integer maxTokens) {

    public static LlmRequest json(List<LlmMessage> messages, double temperature, Integer maxTokens) {
        return new LlmRequest(null, messages, temperature, true, maxTokens);
    }

    public LlmRequest withModel(String resolvedModel) {
        return new LlmRequest(resolvedModel, messages, temperature, jsonMode, maxTokens);
    }
}
