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
package com.github.cronflow.springapp.server;

import java.util.List;
import com.github.cronflow.springapp.server.pojo.StoredDag;

/**
 * Durable storage for DAG definitions ({@code cf_task_dag}). Kept minimal: the registry holds the
 * parsed definitions in memory for execution; this is what survives a restart.
 *
 * @Description: DagStore
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface DagStore {

    /** Insert or update the definition for {@code (application, graph)} in the given format. */
    void save(String application, String graph, String definition, String format);

    /** Every stored definition (as JPA-free runtime objects), for warming the registry on startup. */
    List<StoredDag> loadAll();

}
