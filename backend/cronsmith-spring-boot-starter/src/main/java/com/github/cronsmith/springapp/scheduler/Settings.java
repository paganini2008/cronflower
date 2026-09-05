package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 
 * Shared defaults for the task extension.
 * 
 * <p>
 * Everything is anchored to UTC on purpose. Stored fire times carry no zone of their own, so if one
 * part of the system read the wall clock in a local zone and another in UTC, a task would appear to
 * be hours late or early. Per-task zone handling belongs to the cron expression, which does its own
 * conversion before producing a fire time.
 * 
 * @Description: Settings
 * @Author: Fred Feng
 * @Date: 14/04/2025
 * @Version 1.0.0
 */
public abstract class Settings {

    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("UTC");

    /**
     * How long the clock waits between ticks, in milliseconds. Also the resolution of the timing
     * wheel's lowest level, and therefore the finest interval a task can be scheduled at.
     */
    public static final long DEFAULT_TICK_DURATION = 1000L;

    /**
     * Slots per level of the timing wheel. Level n covers {@code tick * size^(n+1)}, so with a one
     * second tick and 60 slots five levels reach roughly 24 years.
     */
    public static final int DEFAULT_WHEEL_SIZE = 60;

    /**
     * How late a fire time may be before it counts as a misfire, in milliseconds.
     */
    public static final long DEFAULT_MISFIRE_THRESHOLD = 60000L;

    /**
     * How many fire times in the past the scheduler steps over before it gives up catching a task
     * up. Reached only when an expression produces times faster than they can be walked.
     */
    public static final int DEFAULT_MAX_CATCH_UP_STEPS = 1000;

    /**
     * How often, in milliseconds, a windowed scheduler claims newly-due tasks from the store.
     */
    public static final long DEFAULT_CLAIM_INTERVAL_MILLIS = 30000L;

    /**
     * The claim window, in milliseconds, a windowed scheduler falls back to when none is configured
     * (for example a sharded deployment, which requires windowed claiming).
     */
    public static final long DEFAULT_CLAIM_WINDOW_MILLIS = 5 * 60_000L;

    /**
     * The current time in the zone every stored timestamp is expressed in.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(DEFAULT_ZONE_ID);
    }

}
