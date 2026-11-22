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
 * Whether a language is permitted by the executor's {@code cronflow.script.languages} allowlist.
 *
 * @Description: ScriptLanguages
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
final class ScriptLanguages {

    private ScriptLanguages() {}

    /** True when the allowlist is empty (all permitted) or contains the language name. */
    static boolean allowed(CronflowScriptProperties props, ScriptLanguage language) {
        var allow = props.getLanguages();
        if (allow == null || allow.isEmpty()) {
            return true;
        }
        return allow.stream().anyMatch(name -> name != null && name.equalsIgnoreCase(language.name()));
    }
}
