package com.github.cronflow.springapp.executor;

import java.util.Map;

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
    public java.util.List<Object> getList(String name) {
        Object v = channels.get(name);
        if (v == null) {
            return java.util.List.of();
        }
        if (v instanceof java.util.List) {
            return (java.util.List<Object>) v;
        }
        if (v instanceof java.util.Collection) {
            return new java.util.ArrayList<>((java.util.Collection<Object>) v);
        }
        return java.util.List.of(v);
    }

    /** The raw channels, read-only. */
    public Map<String, Object> asMap() {
        return channels;
    }

}
