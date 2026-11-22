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
 * A provider-neutral chat response. {@code error} is non-null when the call failed (transport, auth, or
 * a provider error); {@code content} carries the assistant text (expected to be JSON for a generation).
 */
public record LlmResponse(String content, int promptTokens, int completionTokens, String finishReason,
        String error) {

    public boolean ok() {
        return error == null;
    }

    public static LlmResponse of(String content, int promptTokens, int completionTokens,
            String finishReason) {
        return new LlmResponse(content, promptTokens, completionTokens, finishReason, null);
    }

    public static LlmResponse failed(String error) {
        return new LlmResponse(null, 0, 0, null, error);
    }
}
