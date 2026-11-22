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
package com.github.cronflow.springapp.executor.script;

/**
 * Factory for {@link ScriptResult} values that never started a process (config or dispatch errors).
 *
 * @Description: ScriptResults
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
final class ScriptResults {

    private ScriptResults() {}

    /** A runner-level failure (not a non-zero exit): no process ran. */
    static ScriptResult error(String detail) {
        return new ScriptResult(false, -1, false, "", "", null, 0L, detail);
    }
}
