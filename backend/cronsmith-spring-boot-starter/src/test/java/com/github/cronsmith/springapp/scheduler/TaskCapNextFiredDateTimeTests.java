package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Boundary coverage for {@link Task#capNextFiredDateTime}, the shared gate every task manager uses
 * to apply {@code repeatCount} and {@code stopAt}. Kept as a pure unit test so the exact edges —
 * inclusive vs exclusive — are pinned without any clock or database in the way.
 *
 * @Description: TaskCapNextFiredDateTimeTests
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 1.0.0
 */
class TaskCapNextFiredDateTimeTests {

    private static final LocalDateTime STOP = LocalDateTime.of(2027, 1, 1, 12, 0, 0);
    private static final LocalDateTime NEXT = LocalDateTime.of(2026, 6, 1, 9, 0, 0);

    // ---- stopAt boundary: the deadline is INCLUSIVE (isAfter is strict) ----

    @Test
    void fireExactlyAtStopAtIsAllowed() {
        // next == stopAt is not "after" stopAt, so the occurrence on the deadline still runs.
        assertThat(Task.capNextFiredDateTime(STOP, 0, -1, STOP)).isEqualTo(STOP);
    }

    @Test
    void oneNanoPastStopAtIsCapped() {
        assertThat(Task.capNextFiredDateTime(STOP.plusNanos(1), 0, -1, STOP)).isNull();
    }

    @Test
    void beforeStopAtIsAllowed() {
        assertThat(Task.capNextFiredDateTime(NEXT, 0, -1, STOP)).isEqualTo(NEXT);
    }

    @Test
    void nullStopAtNeverCaps() {
        assertThat(Task.capNextFiredDateTime(NEXT, 0, -1, null)).isEqualTo(NEXT);
    }

    // ---- repeatCount boundary: fires 1..repeatCount, capped once runCount reaches it ----

    @Test
    void lastAllowedRunIsScheduled() {
        // repeatCount 3, two runs done: the third occurrence is still allowed.
        assertThat(Task.capNextFiredDateTime(NEXT, 2, 3, null)).isEqualTo(NEXT);
    }

    @Test
    void reachingTheCapStops() {
        // repeatCount 3, three runs done: no fourth occurrence.
        assertThat(Task.capNextFiredDateTime(NEXT, 3, 3, null)).isNull();
    }

    @Test
    void zeroOrNegativeRepeatCountIsUnlimited() {
        assertThat(Task.capNextFiredDateTime(NEXT, 1_000_000, 0, null)).isEqualTo(NEXT);
        assertThat(Task.capNextFiredDateTime(NEXT, 1_000_000, -1, null)).isEqualTo(NEXT);
    }

    // ---- both limits + the no-next-occurrence passthrough ----

    @Test
    void nullNextStaysNull() {
        assertThat(Task.capNextFiredDateTime(null, 0, 5, STOP)).isNull();
    }

    @Test
    void repeatCapWinsEvenBeforeTheDeadline() {
        assertThat(Task.capNextFiredDateTime(NEXT, 5, 5, STOP)).isNull();
    }
}
