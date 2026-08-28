package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;

/**
 * The envelope exchanged on the {@code cronsmith.taskmanager} channel. Three shapes:
 *
 * <ul>
 * <li>{@code WRITE} — a follower forwards a write to the leader; carries {@code requestId}, {@code op}
 * and {@code args}.</li>
 * <li>{@code RESPONSE} — the leader answers a {@code WRITE}; carries {@code requestId} and either
 * {@code result} or {@code error}.</li>
 * <li>{@code APPLY} — the leader broadcasts a committed write to be replayed on node-local stores;
 * carries {@code op} and {@code args}, no reply expected.</li>
 * </ul>
 *
 * Serialized with the core {@code SerializationUtils}, so every element of {@code args} (and any
 * {@code result}) must be {@link Serializable} — which the task model already is.
 *
 * @Description: ClusterMessage
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class ClusterMessage implements Serializable {

    private static final long serialVersionUID = 7901254433128765401L;

    public enum Type {
        WRITE, RESPONSE, APPLY
    }

    private final Type type;
    private final String requestId;
    private final ClusterOp op;
    private final Object[] args;
    private final Object result;
    private final String error;

    private ClusterMessage(Type type, String requestId, ClusterOp op, Object[] args, Object result,
            String error) {
        this.type = type;
        this.requestId = requestId;
        this.op = op;
        this.args = args;
        this.result = result;
        this.error = error;
    }

    public static ClusterMessage write(String requestId, ClusterOp op, Object[] args) {
        return new ClusterMessage(Type.WRITE, requestId, op, args, null, null);
    }

    public static ClusterMessage response(String requestId, Object result, String error) {
        return new ClusterMessage(Type.RESPONSE, requestId, null, null, result, error);
    }

    public static ClusterMessage apply(ClusterOp op, Object[] args) {
        return new ClusterMessage(Type.APPLY, null, op, args, null, null);
    }

    public Type type() {
        return type;
    }

    public String requestId() {
        return requestId;
    }

    public ClusterOp op() {
        return op;
    }

    public Object[] args() {
        return args;
    }

    public Object result() {
        return result;
    }

    public String error() {
        return error;
    }

}
