package com.github.cronsmith.springapp.scheduler;

/**
 * The scheduler's reply to a registration: the {@code instanceId} it assigned this executor. The
 * executor keeps it and sends it back on every subsequent register / heartbeat, so the scheduler
 * refreshes the same registry entry (rather than accumulating one per heartbeat). A blank id in the
 * request means "first registration, please mint one".
 *
 * @Description: RegistrationResponse
 * @Author: Fred Feng
 * @Date: 28/08/2026
 * @Version 1.0.0
 */
public record RegistrationResponse(String instanceId) {
}
