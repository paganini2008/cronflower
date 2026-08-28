package com.github.cronsmith.springapp.scheduler;

/**
 * The write operations of {@code TaskManager} that are routed to the leader (and, when the storage
 * is replicated, broadcast to the other nodes). Reads never appear here — they are served locally.
 *
 * @Description: ClusterOp
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public enum ClusterOp {

    SAVE_TASK,
    REMOVE_TASK,
    COMPUTE_NEXT_FIRED,
    SET_STATUS,
    CAS_STATUS,
    RECORD_EXECUTION,
    RECORD_MISFIRE;

}
