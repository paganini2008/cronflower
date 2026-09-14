package com.github.cronflow.springapp.executor;

/**
 * The server's reply to a {@link DagRegistrationRequest}, carrying the assigned instance id.
 *
 * @Description: DagRegistrationResponse
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagRegistrationResponse(String instanceId) {}
