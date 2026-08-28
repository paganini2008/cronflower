package com.github.cronsmith.springapp.executor;

/**
 * The executor's view of the server: register once, heartbeat periodically, report each run.
 *
 * <p>
 * An interface so the transport (here {@link WebClientCronsmithServerClient}) can be swapped, and so
 * tests can stand in a fake. Each call returns {@code true} when a server accepted it.
 *
 * @Description: CronsmithServerClient
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public interface CronsmithServerClient {

    // Sub-paths under the server's API prefix (cronsmith.client.server-api-prefix, default /cronsmith).
    String REGISTER_SUBPATH = "/executors/register";
    String HEARTBEAT_SUBPATH = "/executors/heartbeat";
    String COMPLETE_SUBPATH = "/executions/complete";

    /**
     * Register this executor and saveOrUpdate its tasks. Returns the scheduler-assigned instanceId
     * (which the caller keeps and sends back on later calls), or {@code null} if no server accepted it.
     */
    String register(RegistrationRequest request);

    /** Keep this executor alive in the server's list (no tasks). */
    boolean heartbeat(HeartbeatRequest request);

    /** Report a finished run so the server can log it and decide on a retry. */
    boolean complete(CompleteRequest request);

}
