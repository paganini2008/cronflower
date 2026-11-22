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
 * One node the model may wire into a generated DAG: a real executor bean+method the cluster already
 * knows about, with a description and the channels it reads and writes. The grounding that keeps the
 * model from inventing nodes.
 */
public record CatalogNode(String name, String description, String beanName, String methodName,
        List<String> reads, List<String> writes) {

    public List<String> reads() {
        return reads == null ? List.of() : reads;
    }

    public List<String> writes() {
        return writes == null ? List.of() : writes;
    }
}
