package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.Settings;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TimingWheelTaskQueue;

/**
 * 
 * Drives the timing wheel from a fixed start time rather than the system clock, so that every case
 * below is exact and none of them depend on how long the test takes to run.
 * 
 * @Description: TimingWheelTaskQueueTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TimingWheelTaskQueueTests {

    private static final LocalDateTime START =
            LocalDateTime.of(2026, Month.AUGUST, 24, 10, 0, 0);

    private static final long TICK = 1000L;
    private static final int WHEEL_SIZE = 60;

    private TimingWheelTaskQueue queue;

    @BeforeEach
    public void setUp() {
        queue = new TimingWheelTaskQueue(TICK, WHEEL_SIZE, Settings.DEFAULT_ZONE_ID, START);
    }

    private static TaskId id(String name) {
        return TaskId.of(name);
    }

    @Test
    public void testTaskFiresOnItsOwnTick() {
        assertTrue(queue.offer(START.plusSeconds(3), id("a")));
        assertTrue(queue.advance(START.plusSeconds(1)).isEmpty());
        assertTrue(queue.advance(START.plusSeconds(2)).isEmpty());
        assertEquals(List.of(id("a")), new ArrayList<>(queue.advance(START.plusSeconds(3))));
    }

    @Test
    public void testTaskDueInThePastIsRejected() {
        assertFalse(queue.offer(START.minusSeconds(1), id("a")));
        assertFalse(queue.offer(START, id("a")));
        assertEquals(0, queue.size());
    }

    @Test
    public void testSkippedTicksAreCaughtUp() {
        // The single most important property: a tick that never happened must not swallow the
        // tasks that belonged to it.
        queue.offer(START.plusSeconds(2), id("a"));
        queue.offer(START.plusSeconds(5), id("b"));
        queue.offer(START.plusSeconds(9), id("c"));
        Collection<TaskId> fired = queue.advance(START.plusSeconds(10));
        assertEquals(3, fired.size());
        assertTrue(fired.containsAll(List.of(id("a"), id("b"), id("c"))));
        assertEquals(0, queue.size());
    }

    @Test
    public void testGapWiderThanOneRevolutionStillDrainsEverything() {
        queue.offer(START.plusSeconds(1), id("a"));
        queue.offer(START.plusSeconds(30), id("b"));
        queue.offer(START.plusSeconds(59), id("c"));
        // Two full laps of the lowest level at once.
        Collection<TaskId> fired = queue.advance(START.plusSeconds(120));
        assertEquals(3, fired.size());
        assertEquals(0, queue.size());
    }

    @Test
    public void testCascadeFromTheSecondLevel() {
        // 90 seconds is past the lowest level's one minute span, so this lands a level up and has
        // to come back down before it can fire.
        assertTrue(queue.offer(START.plusSeconds(90), id("a")));
        assertEquals(2, queue.getLevelCount());
        assertTrue(queue.advance(START.plusSeconds(60)).isEmpty());
        assertTrue(queue.advance(START.plusSeconds(89)).isEmpty());
        assertEquals(List.of(id("a")), new ArrayList<>(queue.advance(START.plusSeconds(90))));
    }

    @Test
    public void testCascadeAcrossSeveralLevels() {
        // Just over an hour: three levels deep with a one second tick and sixty slots.
        LocalDateTime due = START.plusSeconds(3700);
        assertTrue(queue.offer(due, id("a")));
        assertEquals(3, queue.getLevelCount());
        assertTrue(queue.advance(START.plusSeconds(3600)).isEmpty());
        assertTrue(queue.advance(due.minusSeconds(1)).isEmpty());
        assertEquals(List.of(id("a")), new ArrayList<>(queue.advance(due)));
    }

    @Test
    public void testCascadeFiresOnTheExactSecondNotTheMinuteBoundary() {
        // A regression guard for the ordering of the two advance passes: cascading an entry down
        // before the lower level's clock has moved makes it look overdue and fire a minute early.
        LocalDateTime due = START.plusSeconds(119);
        queue.offer(due, id("a"));
        for (int second = 1; second < 119; second++) {
            assertTrue(queue.advance(START.plusSeconds(second)).isEmpty(), "fired early at second " + second);
        }
        assertEquals(List.of(id("a")), new ArrayList<>(queue.advance(due)));
    }

    @Test
    public void testVeryDistantTaskGrowsTheWheelAndStillFires() {
        LocalDateTime due = START.plusYears(2);
        assertTrue(queue.offer(due, id("a")));
        assertTrue(queue.getLevelCount() >= 4);
        assertTrue(queue.advance(due.minusSeconds(1)).isEmpty());
        assertEquals(List.of(id("a")), new ArrayList<>(queue.advance(due)));
    }

    @Test
    public void testReofferingSupersedesThePreviousFireTime() {
        queue.offer(START.plusSeconds(2), id("a"));
        queue.offer(START.plusSeconds(8), id("a"));
        assertEquals(1, queue.size());
        assertTrue(queue.advance(START.plusSeconds(5)).isEmpty());
        assertEquals(List.of(id("a")), new ArrayList<>(queue.advance(START.plusSeconds(8))));
    }

    @Test
    public void testRemovedTaskDoesNotFire() {
        queue.offer(START.plusSeconds(3), id("a"));
        queue.offer(START.plusSeconds(3), id("b"));
        assertTrue(queue.remove(id("a")));
        assertFalse(queue.remove(id("a")));
        assertEquals(List.of(id("b")), new ArrayList<>(queue.advance(START.plusSeconds(3))));
    }

    @Test
    public void testRemovedTaskDoesNotFireAfterCascading() {
        queue.offer(START.plusSeconds(90), id("a"));
        queue.advance(START.plusSeconds(60));
        assertTrue(queue.remove(id("a")));
        assertTrue(queue.advance(START.plusSeconds(90)).isEmpty());
    }

    @Test
    public void testContainsAndSize() {
        assertFalse(queue.contains(id("a")));
        queue.offer(START.plusSeconds(3), id("a"));
        queue.offer(START.plusSeconds(4), id("b"));
        assertTrue(queue.contains(id("a")));
        assertEquals(2, queue.size());
        queue.advance(START.plusSeconds(3));
        assertFalse(queue.contains(id("a")));
        assertEquals(1, queue.size());
    }

    @Test
    public void testClear() {
        queue.offer(START.plusSeconds(3), id("a"));
        queue.offer(START.plusSeconds(3000), id("b"));
        queue.clear();
        assertEquals(0, queue.size());
        assertTrue(queue.advance(START.plusSeconds(3000)).isEmpty());
    }

    @Test
    public void testManyTasksOnTheSameTick() {
        for (int i = 0; i < 500; i++) {
            queue.offer(START.plusSeconds(5), id("task-" + i));
        }
        assertEquals(500, queue.size());
        assertEquals(500, queue.advance(START.plusSeconds(5)).size());
        assertEquals(0, queue.size());
    }

    @Test
    public void testTasksSpreadAcrossEveryLevelAllFire() {
        LocalDateTime[] dues = {START.plusSeconds(1), START.plusSeconds(59),
                START.plusSeconds(61), START.plusMinutes(59), START.plusHours(2),
                START.plusDays(3), START.plusDays(200)};
        for (int i = 0; i < dues.length; i++) {
            assertTrue(queue.offer(dues[i], id("task-" + i)));
        }
        assertEquals(dues.length, queue.size());
        List<TaskId> fired = new ArrayList<>();
        // Walk the clock in one-hour steps; every task must come out exactly once.
        for (LocalDateTime cursor = START; !cursor.isAfter(START.plusDays(201));
                cursor = cursor.plusHours(1)) {
            fired.addAll(queue.advance(cursor));
        }
        assertEquals(dues.length, fired.size());
        assertEquals(0, queue.size());
    }

    @Test
    public void testAdvanceWithoutMovingReturnsNothing() {
        queue.offer(START.plusSeconds(3), id("a"));
        assertTrue(queue.advance(START).isEmpty());
        assertTrue(queue.advance(START).isEmpty());
        assertEquals(1, queue.size());
    }

    @Test
    public void testNullArgumentsAreIgnored() {
        assertFalse(queue.offer(null, id("a")));
        assertFalse(queue.offer(START.plusSeconds(1), null));
        assertFalse(queue.remove(null));
        assertFalse(queue.contains(null));
        assertTrue(queue.advance(null).isEmpty());
    }

    @Test
    public void testDefaultConstructorUsesTheSystemClock() {
        TimingWheelTaskQueue defaultQueue = new TimingWheelTaskQueue();
        assertTrue(defaultQueue.offer(Settings.now().plusSeconds(30), id("a")));
        assertEquals(1, defaultQueue.size());
        assertEquals(1, defaultQueue.getLevelCount());
    }

    @Test
    public void testTickAndSizeConstructor() {
        TimingWheelTaskQueue coarse = new TimingWheelTaskQueue(100L, 10);
        assertTrue(coarse.offer(Settings.now().plusSeconds(5), id("a")));
        assertTrue(coarse.getLevelCount() >= 2);
    }

    @Test
    public void testNonPositiveTickIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        new TimingWheelTaskQueue(0L, WHEEL_SIZE);
    
        });
    }

    @Test
    public void testNonPositiveWheelSizeIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        new TimingWheelTaskQueue(TICK, 0);
    
        });
    }

}
