package com.github.cronflow.springapp.executor;

/**
 * The executor's view of the cronflow server: register DAG definitions + trigger bindings, and
 * heartbeat. Mirrors {@code CronsmithServerClient}; the implementation fails over across the
 * configured server URLs.
 *
 * @Description: CronflowServerClient
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public interface CronflowServerClient {

    String REGISTER_SUBPATH = "/dags/register";
    String HEARTBEAT_SUBPATH = "/dags/heartbeat";

    /** Register this executor and its DAGs; returns the assigned instance id, or null on failure. */
    String register(DagRegistrationRequest request);

    /** Send a heartbeat; returns whether a server accepted it. */
    boolean heartbeat(DagHeartbeatRequest request);

}
