package com.github.cronflow.springapp.executor;

/**
 * Holds this executor's server-assigned instance id, stable across re-registrations. Shared so the
 * registrar and any diagnostics report the same identity.
 *
 * @Description: ExecutorIdentity
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class ExecutorIdentity {

    private volatile String instanceId;

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

}
