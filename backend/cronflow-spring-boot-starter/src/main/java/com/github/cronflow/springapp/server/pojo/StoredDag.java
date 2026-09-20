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
package com.github.cronflow.springapp.server.pojo;

/**
 * A stored DAG definition as a plain runtime object, independent of any persistence tech (JPA or
 * jOOQ). {@link DagStore#loadAll()} returns these so the store contract — and the jOOQ store — never
 * depend on the JPA entities.
 *
 * @Description: StoredDag
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record StoredDag(String application, String graph, String definition, String format) {}
