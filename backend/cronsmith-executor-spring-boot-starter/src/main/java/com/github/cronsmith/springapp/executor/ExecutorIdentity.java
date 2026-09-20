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
package com.github.cronsmith.springapp.executor;

/**
 * This executor's identity for the execution log, shared between the registrar (which owns it) and the
 * task execution service (which stamps it onto each {@link CompleteRequest}).
 *
 * <p>
 * The {@code instanceId} is assigned by the scheduler on the first successful registration; until then
 * it is {@code null}. {@link #repr()} is {@code applicationName,instanceId,host:port} — the form the
 * server stores as {@code executor_repr}. Set by {@link CronsmithClientRegistrar} after each register.
 *
 * @Description: ExecutorIdentity
 * @Author: Fred Feng
 * @Date: 28/08/2026
 * @Version 1.0.0
 */
public class ExecutorIdentity {

    private volatile String instanceId;
    private volatile String repr;

    /** The scheduler-assigned instance id, or {@code null} before the first successful registration. */
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /** {@code applicationName,instanceId,host:port}, or {@code null} before the first registration. */
    public String repr() {
        return repr;
    }

    public void setRepr(String repr) {
        this.repr = repr;
    }
}
