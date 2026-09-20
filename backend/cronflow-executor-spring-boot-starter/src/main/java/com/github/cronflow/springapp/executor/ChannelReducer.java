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
package com.github.cronflow.springapp.executor;

/**
 * The reducers a {@link Channel} can name — the DAG-kernel built-ins plus cronflow's own — so a channel
 * picks one from a closed, discoverable set instead of a free-form string. For an application-defined
 * reducer (a {@code Reducer} bean on the server), use {@link Channel#customReducer()} instead, which
 * overrides this. Each constant carries the reducer's registered name, which is what travels in the
 * woven definition and is looked up in the server's catalog.
 *
 * @Description: ChannelReducer
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public enum ChannelReducer {

    // --- DAG-kernel built-ins ---
    /** Last write wins (the default). */
    LAST_WINS("lastWins"),
    /** First write wins; later writes are ignored. */
    FIRST_WINS("firstWins"),
    /** Concatenate lists in write order. */
    CONCAT_LIST("concatList"),
    /** Union of sets. */
    UNION_SET("unionSet"),
    /** Shallow-merge maps. */
    MERGE_MAP("mergeMap"),
    /** Sum as long (null = 0). */
    SUM_LONG("sumLong"),
    /** Sum as int (null = 0). */
    SUM_INT("sumInt"),
    /** Reject a second write. */
    WRITE_ONCE("writeOnce"),

    // --- cronflow built-ins ---
    /** Keep the larger number (Integer/Long/Double). */
    MAX("max"),
    /** Keep the smaller number (Integer/Long/Double). */
    MIN("min"),
    /** Logical AND across writers. */
    AND("and"),
    /** Logical OR across writers. */
    OR("or"),
    /** Concatenate strings in write order. */
    CONCAT_STRING("concatString"),
    /** Join strings with a comma, skipping empties. */
    JOIN_CSV("joinCsv");

    private final String reducerName;

    ChannelReducer(String reducerName) {
        this.reducerName = reducerName;
    }

    /** The registered reducer name used in the woven definition and looked up on the server. */
    public String reducerName() {
        return reducerName;
    }

}
