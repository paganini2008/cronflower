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
import com.github.cronflow.springapp.ai.DagValidator.Issue;
import com.github.cronflow.springapp.pojo.DagDefinition;

/**
 * The outcome of one generation: the drafted DAG (null when it could not be parsed), any validation
 * issues that survived the repair loop, the raw model JSON, and the provider, model, token and repair
 * accounting. A candidate is {@link #valid()} only with a DAG, no issues, and no error, and even then it
 * is a proposal for human review, never auto-run.
 */
public record DagCandidate(DagDefinition dag, List<Issue> issues, String rawJson, String provider,
        String model, int promptTokens, int completionTokens, int repairs, String error) {

    public boolean valid() {
        return error == null && dag != null && (issues == null || issues.isEmpty());
    }

    public static DagCandidate failed(String provider, String model, String error) {
        return new DagCandidate(null, List.of(), null, provider, model, 0, 0, 0, error);
    }
}
