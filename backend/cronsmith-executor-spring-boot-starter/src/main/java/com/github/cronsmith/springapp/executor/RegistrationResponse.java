package com.github.cronsmith.springapp.executor;

/**
 * The server's reply to a registration: the {@code instanceId} it assigned this executor. The
 * executor keeps it and sends it back on every subsequent register / heartbeat.
 *
 * @Description: RegistrationResponse
 * @Author: Fred Feng
 * @Date: 28/08/2026
 * @Version 1.0.0
 */
public record RegistrationResponse(String instanceId) {
}
