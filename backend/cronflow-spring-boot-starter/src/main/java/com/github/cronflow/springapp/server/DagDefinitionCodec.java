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

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import com.github.cronflow.springapp.server.pojo.DagDefinition;

/**
 * (De)serializes a {@link DagDefinition} to/from the text stored in {@code cf_task_dag}, in either
 * JSON or YAML. The stored {@code format} column says which, so a definition round-trips whichever
 * way it was written.
 *
 * @Description: DagDefinitionCodec
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class DagDefinitionCodec {

    public static final String JSON = "json";
    public static final String YAML = "yaml";

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public DagDefinitionCodec(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = YAMLMapper.builder().build();
    }

    /** Serialize in the given format ({@code json}/{@code yaml}); unknown falls back to JSON. */
    public String encode(DagDefinition dag, String format) {
        return mapper(format).writeValueAsString(dag);
    }

    /** Parse text known to be in {@code format}. */
    public DagDefinition decode(String text, String format) {
        return mapper(format).readValue(text, DagDefinition.class);
    }

    /** Canonical format string, defaulting anything non-YAML to {@code json}. */
    public String normalize(String format) {
        return YAML.equalsIgnoreCase(format) ? YAML : JSON;
    }

    private ObjectMapper mapper(String format) {
        return YAML.equalsIgnoreCase(format) ? yamlMapper : jsonMapper;
    }

}
