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
package com.github.cronflow.springapp.executor.pojo;

/**
 * Binds a cronsmith task (identified by group + name) to a DAG. Woven from {@link DagTrigger} and
 * registered to the server, which uses it to fire {@code graph} with the task's return value when the
 * task ends.
 *
 * @Description: TriggerBinding
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record TriggerBinding(String taskGroup, String taskName, String graph) {}
