package com.github.cronsmith.springapp.scheduler;

import java.io.Serializable;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry.ExecutorInstance;

/**
 * A change to the executor list, exchanged on the {@code cronsmith.executors} channel: a follower
 * forwards one to the leader, and the leader multicasts it to everyone else.
 *
 * @Description: ExecutorSyncMessage
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public record ExecutorSyncMessage(Op op, ExecutorInstance instance) implements Serializable {

    public enum Op {
        UPSERT, REMOVE
    }

    public static ExecutorSyncMessage upsert(ExecutorInstance instance) {
        return new ExecutorSyncMessage(Op.UPSERT, instance);
    }

    public static ExecutorSyncMessage remove(ExecutorInstance instance) {
        return new ExecutorSyncMessage(Op.REMOVE, instance);
    }

}
