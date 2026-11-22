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

import java.time.LocalDateTime;

/**
 * An audit record of one generation, stored in {@code cf_ai_generation}. Status is {@code draft} (a
 * usable candidate awaiting review), {@code approved} (registered as a runnable DAG), or {@code failed}
 * (no usable candidate). Secrets are never recorded; {@code candidate} is the DAG JSON, {@code issues}
 * the residual validation notes.
 */
public record AiGenerationRecord(String id, String prompt, String provider, String model, String status,
        String graph, String application, String candidate, String issues, int promptTokens,
        int completionTokens, int repairs, String error, String createdBy, LocalDateTime createdAt,
        String approvedBy, LocalDateTime approvedAt) {

    public static final String DRAFT = "draft";
    public static final String APPROVED = "approved";
    public static final String FAILED = "failed";
}
