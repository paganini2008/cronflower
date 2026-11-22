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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory {@link AiGenerationStore} for deployments without a DataSource and for tests. */
public class InMemoryAiGenerationStore implements AiGenerationStore {

    private final ConcurrentMap<String, AiGenerationRecord> records = new ConcurrentHashMap<>();

    @Override
    public String save(AiGenerationRecord record) {
        records.put(record.id(), record);
        return record.id();
    }

    @Override
    public Optional<AiGenerationRecord> find(String id) {
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public void approve(String id, String approver, String application, String graph) {
        records.computeIfPresent(id, (k, r) -> new AiGenerationRecord(r.id(), r.prompt(), r.provider(),
                r.model(), AiGenerationRecord.APPROVED, graph, application, r.candidate(), r.issues(),
                r.promptTokens(), r.completionTokens(), r.repairs(), r.error(), r.createdBy(),
                r.createdAt(), approver, LocalDateTime.now()));
    }

    @Override
    public List<AiGenerationRecord> recent(int limit) {
        return records.values().stream()
                .sorted(Comparator.comparing(AiGenerationRecord::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(0, limit)).toList();
    }
}
