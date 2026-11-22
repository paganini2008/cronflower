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
import java.util.Optional;

/** Persists generation audit records. A jOOQ backend (self-creating {@code cf_ai_generation}) when a
 *  DataSource is present, an in-memory fallback otherwise. */
public interface AiGenerationStore {

    /** Insert a record. Returns its id. */
    String save(AiGenerationRecord record);

    Optional<AiGenerationRecord> find(String id);

    /** Mark a draft approved and record who approved it and the application and graph it registered as. */
    void approve(String id, String approver, String application, String graph);

    /** Most recent records first, for a history listing. */
    List<AiGenerationRecord> recent(int limit);
}
