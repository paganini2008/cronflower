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

import java.util.Map;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;

/**
 * The run state handed to a DAG node method on the client: an immutable view over the channels the
 * server sent (a plain map off the wire), with typed getters for convenience.
 *
 * <p>
 * This is a cronflow-local type on purpose — the executor is a pure callee and does <b>not</b> depend
 * on the DAG kernel (openspreader). A node reads channels here and returns the ones it changed as a
 * {@code Map<String,Object>}; the server does the merging/reducing.
 *
 * @Description: DagState
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public final class DagState {

    private final Map<String, Object> channels;

    private DagState(Map<String, Object> channels) {
        this.channels = channels == null ? Map.of() : channels;
    }

    public static DagState of(Map<String, Object> channels) {
        return new DagState(channels);
    }

    public boolean contains(String name) {
        return channels.containsKey(name);
    }

    public Object get(String name) {
        return channels.get(name);
    }

    public String getString(String name) {
        Object v = channels.get(name);
        return v == null ? null : String.valueOf(v);
    }

    public long getLong(String name) {
        Object v = channels.get(name);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return v == null ? 0L : Long.parseLong(v.toString());
    }

    public int getInt(String name) {
        Object v = channels.get(name);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return v == null ? 0 : Integer.parseInt(v.toString());
    }

    public double getDouble(String name) {
        Object v = channels.get(name);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return v == null ? 0.0 : Double.parseDouble(v.toString());
    }

    public boolean getBoolean(String name) {
        Object v = channels.get(name);
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && Boolean.parseBoolean(v.toString());
    }

    /** A channel read as a list; a single non-list value is wrapped, an absent one is empty. Handy for a
     *  sharded node's per-shard handler reading {@code Shard.CHANNEL}. */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String name) {
        Object v = channels.get(name);
        if (v == null) {
            return List.of();
        }
        if (v instanceof List) {
            return (List<Object>) v;
        }
        if (v instanceof Collection) {
            return new ArrayList<>((Collection<Object>) v);
        }
        return List.of(v);
    }

    /** The raw channels, read-only. */
    public Map<String, Object> asMap() {
        return channels;
    }

}
