package com.github.cronsmith.springapp.example;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.github.cronsmith.springapp.executor.Task;

/**
 * A guided tour of {@link Task}. Every method here is auto-discovered on startup, turned into a task
 * definition and registered with the cronsmith server; the server owns the schedule and calls back
 * into this bean when a task is due.
 *
 * <p>
 * The examples are grouped by the feature they show off, so this file doubles as a copy-paste
 * reference for the annotation:
 * <ul>
 * <li><b>Schedules</b> — traditional cron, YCRON (year-based), fixed interval, ISO-8601 duration.</li>
 * <li><b>Parameters</b> — a constant, a SpEL template evaluated on every fire, and the no-arg form.</li>
 * <li><b>Reliability</b> — retry with back-off, per-run timeout, and the misfire policy.</li>
 * <li><b>Organisation</b> — explicit group / name / description for the console.</li>
 * </ul>
 *
 * <p>
 * A method may take no arguments or a single {@link String} (the task's {@code initialParameter}); a
 * non-void return value is reported back to the server and kept in the execution log.
 */
@Component
public class DemoTasks {

    private static final Logger log = LoggerFactory.getLogger(DemoTasks.class);

    // --------------------------------------------------------------------------------------------
    // Schedules
    // --------------------------------------------------------------------------------------------

    /** Traditional (month-based) cron: every 5 seconds. Takes its initialParameter as a String. */
    @Task(cron = "*/5 * * * * ?", group = "showcase", name = "sayHello", initialParameter = "world",
            description = "greets whoever is passed in, every 5s")
    public String sayHello(String who) {
        String message = "hello, " + who + " @ " + LocalDateTime.now();
        log.info("[sayHello] {}", message);
        return message;
    }

    /**
     * Traditional cron, top of every hour, no argument. Its initialParameter is a SpEL template that
     * is evaluated on THIS executor at run time — here, today's date as a fresh value on every fire.
     */
    @Task(cron = "0 0 * * * ?", group = "showcase", name = "heartbeatTick",
            initialParameter = "#{T(java.time.LocalDate).now().toString()}",
            description = "hourly heartbeat; parameter computed on the executor via SpEL")
    public void heartbeatTick(String today) {
        log.info("[heartbeatTick] fired for {}", today);
    }

    /**
     * YCRON (year-based extension, {@code parser = "ycron"}): fields are
     * {@code <sec> <min> <hour> <dow> <woy> <doy> <year>}. Here — noon on the 200th day of every
     * year, a schedule no traditional cron field can express (day-of-year).
     */
    @Task(cron = "0 0 12 ? ? 200", parser = "ycron", group = "showcase", name = "dayOfYear200",
            description = "YCRON: noon on the 200th day of the year")
    public void dayOfYear200() {
        log.info("[dayOfYear200] fired — YCRON day-of-year schedule");
    }

    /**
     * YCRON with day-of-week + week-of-year together: 09:00 on weekdays that fall in ISO week 1 of
     * each year. {@code doy} is {@code ?} because dow+woy and doy are mutually exclusive.
     */
    @Task(cron = "0 0 9 MON-FRI 1 ?", parser = "ycron", group = "showcase", name = "firstWeekMornings",
            description = "YCRON: 09:00 on weekdays of the first week of the year")
    public void firstWeekMornings() {
        log.info("[firstWeekMornings] fired — YCRON week-of-year schedule");
    }

    /** Fixed interval via interval + unit — fires every 10 seconds. Simplest "every N" schedule. */
    @Task(interval = 10, intervalUnit = TimeUnit.SECONDS, group = "showcase", name = "every10s",
            description = "fixed interval, every 10 seconds")
    public String every10s() {
        log.info("[every10s] fired");
        return "ok";
    }

    /** Fixed interval as an ISO-8601 duration — every 90 minutes, which no single cron field spans. */
    @Task(iso = "PT1H30M", group = "showcase", name = "every90m",
            description = "ISO-8601 duration, every 90 minutes")
    public void every90m() {
        log.info("[every90m] fired");
    }

    // --------------------------------------------------------------------------------------------
    // Reliability: retry, timeout, misfire
    // --------------------------------------------------------------------------------------------

    private final AtomicInteger flakyCounter = new AtomicInteger();

    /**
     * Retry with back-off, driven by the server ({@code maxRetryCount = 2}, {@code retryInterval}).
     * Fails the first two attempts of each fire and succeeds on the third, so a single fire shows up
     * as three rows in the execution log (attempt 0 fail, 1 fail, 2 success).
     */
    @Task(cron = "*/20 * * * * ?", group = "reliability", name = "flaky", maxRetryCount = 2,
            retryInterval = 1000, description = "fails twice then succeeds — demonstrates server retry")
    public String flaky(String parameter) {
        int n = flakyCounter.incrementAndGet();
        if (n % 3 != 0) {
            log.info("[flaky] attempt #{} -> FAIL", n);
            throw new IllegalStateException("flaky failure #" + n);
        }
        log.info("[flaky] attempt #{} -> OK", n);
        return "flaky ok at " + n;
    }

    /**
     * Per-run timeout ({@code timeout = 2000} ms): this run deliberately sleeps 5s, so the server
     * marks it timed-out. Useful for seeing how a slow task is reported in the console.
     */
    @Task(cron = "0 */5 * * * ?", group = "reliability", name = "slowJob", timeout = 2000,
            description = "sleeps 5s against a 2s timeout — demonstrates timeout enforcement")
    public String slowJob() throws InterruptedException {
        log.info("[slowJob] starting a 5s unit of work (timeout is 2s)");
        Thread.sleep(5000);
        log.info("[slowJob] finished");
        return "done";
    }

    /**
     * Misfire policy: if the scheduler was down and this fire time passed, {@code SKIP} it rather
     * than firing late. The alternatives are {@code FIRE_ONCE_NOW} (the default) and {@code FIRE_ALL}.
     */
    @Task(cron = "0 0 2 * * ?", group = "reliability", name = "nightlyRollup", misfirePolicy = "SKIP",
            description = "02:00 daily; skips missed fires instead of catching up")
    public void nightlyRollup() {
        log.info("[nightlyRollup] fired");
    }

}
