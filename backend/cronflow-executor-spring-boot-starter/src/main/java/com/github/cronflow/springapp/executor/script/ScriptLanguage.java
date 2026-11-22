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
 * The languages a script script can be written in. {@code SHELL} and {@code PYTHON} run as an OS process
 * (local host or container). {@code SQL} runs over JDBC against a configured datasource. {@code JAVA}
 * is reserved for a later slice (single-file {@code java Foo.java}).
  *
 * @Description: ScriptLanguage
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
public enum ScriptLanguage {

    SHELL("sh"),
    PYTHON("py"),
    SQL("sql"),
    JAVA("java");

    private final String fileExtension;

    ScriptLanguage(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    /** The script file suffix used when the runner materializes the source to disk. */
    public String fileExtension() {
        return fileExtension;
    }

    /** Lenient parse: accepts case-insensitive names, returns null for a blank or unknown value. */
    public static ScriptLanguage from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (ScriptLanguage l : values()) {
            if (l.name().equalsIgnoreCase(raw.trim())) {
                return l;
            }
        }
        return null;
    }
}
