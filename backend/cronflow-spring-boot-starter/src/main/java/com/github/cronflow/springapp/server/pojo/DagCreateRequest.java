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

import java.io.Serializable;

/**
 * A DAG authored in the console: the {@code application} whose executor hosts the node beans, and the
 * {@link DagDefinition} the user drew on the canvas. The server registers it against a live executor of
 * that application, so its nodes dispatch there exactly like an annotation-registered graph.
 *
 * @Description: DagCreateRequest
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagCreateRequest(String application, DagDefinition definition) implements Serializable {}
