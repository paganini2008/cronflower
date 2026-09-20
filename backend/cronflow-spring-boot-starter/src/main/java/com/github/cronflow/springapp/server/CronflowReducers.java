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

import java.util.List;
import com.chaconneai.openspreader.dag.Reducer;

/**
 * Extra channel reducers cronflow registers into its {@code GraphCatalog} on top of the openspreader
 * kernel's eight built-ins (lastWins, firstWins, concatList, unionSet, mergeMap, sumLong, sumInt,
 * writeOnce) — so common aggregations are available to {@code @Channel(reducer="...")} without the
 * application declaring a bean. The kernel is not modified; these live on the cronflow side.
 *
 * <p>
 * Names: {@code max}, {@code min} (numeric, any Integer/Long/Double — the extreme by value, keeping the
 * original type), {@code and}, {@code or} (boolean), {@code concatString} (append), {@code joinCsv}
 * (comma-joined). Reducers see the current channel value and the incoming write; the coordinator only
 * calls them for the second and later writes to a channel, so the first write seeds it as-is.
 *
 * @Description: CronflowReducers
 * @Author: Fred Feng
 * @Version 1.0.0
 */
final class CronflowReducers {

    private CronflowReducers() {}

    static List<Reducer<?>> all() {
        return List.of(max(), min(), and(), or(), concatString(), joinCsv());
    }

    /** Keep the larger of two numbers, preserving the original boxed type. */
    static Reducer<Number> max() {
        return Reducer.named("max", (current, incoming) -> {
            if (current == null) {
                return incoming;
            }
            if (incoming == null) {
                return current;
            }
            return incoming.doubleValue() > current.doubleValue() ? incoming : current;
        });
    }

    /** Keep the smaller of two numbers, preserving the original boxed type. */
    static Reducer<Number> min() {
        return Reducer.named("min", (current, incoming) -> {
            if (current == null) {
                return incoming;
            }
            if (incoming == null) {
                return current;
            }
            return incoming.doubleValue() < current.doubleValue() ? incoming : current;
        });
    }

    /** Logical AND across the branches that write the channel (null is the identity, true). */
    static Reducer<Boolean> and() {
        return Reducer.named("and",
                (current, incoming) -> (current == null || current) && (incoming == null || incoming));
    }

    /** Logical OR across the branches that write the channel (null is the identity, false). */
    static Reducer<Boolean> or() {
        return Reducer.named("or",
                (current, incoming) -> (current != null && current) || (incoming != null && incoming));
    }

    /** Concatenate strings in write order (null treated as empty). */
    static Reducer<String> concatString() {
        return Reducer.named("concatString",
                (current, incoming) -> (current == null ? "" : current) + (incoming == null ? "" : incoming));
    }

    /** Join strings with a comma, skipping empties, in write order. */
    static Reducer<String> joinCsv() {
        return Reducer.named("joinCsv", (current, incoming) -> {
            if (current == null || current.isEmpty()) {
                return incoming == null ? "" : incoming;
            }
            if (incoming == null || incoming.isEmpty()) {
                return current;
            }
            return current + "," + incoming;
        });
    }

}
