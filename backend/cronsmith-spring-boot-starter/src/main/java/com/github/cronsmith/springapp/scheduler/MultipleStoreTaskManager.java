package com.github.cronsmith.springapp.scheduler;

import com.github.cronsmith.springapp.scheduler.TaskManager;

/**
 * A {@link TaskManager} that is aware of the {@link StoreType} backing it — a distributed-only concern
 * the single-node core does not carry. Lets callers tell a node-local store from a shared one (e.g. to
 * decide whether group sharding is possible, or how writes replicate) without knowing the concrete
 * implementation.
 *
 * @Description: MultipleStoreTaskManager
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public interface MultipleStoreTaskManager extends TaskManager {

    /** The kind of store backing this task manager. */
    StoreType getStoreType();

}
