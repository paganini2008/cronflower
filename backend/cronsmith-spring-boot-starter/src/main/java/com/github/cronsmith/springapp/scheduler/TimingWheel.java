package com.github.cronsmith.springapp.scheduler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 
 * One level of a hierarchical timing wheel, in the shape Kafka's timer uses.
 * 
 * <p>
 * A level is a ring of {@code wheelSize} slots, each covering {@code tickDuration} milliseconds, so
 * the level as a whole spans {@code interval = tickDuration * wheelSize}. An entry due beyond that
 * span goes to an overflow level created on demand, whose own tick is this level's whole interval.
 * With a one second tick and sixty slots the levels cover a minute, an hour, two and a half days,
 * five months and then twenty-four years, which is deeper than any cron expression needs.
 * 
 * <p>
 * Slots are addressed by absolute time rather than by a moving cursor: an entry due at {@code e}
 * always lands in slot {@code (e / tickDuration) % wheelSize}. Nothing has to be re-indexed when the
 * clock moves, and a slot's identity does not depend on how many ticks have been observed, so a
 * skipped tick cannot shift everything by one.
 * 
 * <p>
 * Advancing happens in two passes, and the order matters. {@link #advanceClock(long)} moves every
 * level's clock first; only then does {@link #flushExpired(Consumer)} empty the slots that were
 * crossed. Emptying a level before the levels below it had their clocks moved would make an entry
 * cascading down look overdue by up to a full tick of the level above.
 * 
 * <p>
 * This class is not thread safe. {@link TimingWheelTaskQueue} owns the lock.
 * 
 * @Description: TimingWheel
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
class TimingWheel {

    private final long tickDuration;
    private final int wheelSize;
    private final long interval;
    private final List<Set<TimerEntry>> buckets;

    /** Start of the tick the clock currently sits in, always a multiple of tickDuration. */
    private long currentTime;

    /** The next level up, created the first time something is due beyond this level's span. */
    private TimingWheel overflow;

    /** Slots crossed by the last {@link #advanceClock(long)} and not yet flushed. */
    private long pendingFrom = -1L;
    private long pendingTo = -1L;

    TimingWheel(long tickDuration, int wheelSize, long startTime) {
        if (tickDuration <= 0) {
            throw new IllegalArgumentException("Tick duration must be positive: " + tickDuration);
        }
        if (wheelSize <= 0) {
            throw new IllegalArgumentException("Wheel size must be positive: " + wheelSize);
        }
        this.tickDuration = tickDuration;
        this.wheelSize = wheelSize;
        this.interval = tickDuration * wheelSize;
        this.buckets = new ArrayList<>(wheelSize);
        for (int i = 0; i < wheelSize; i++) {
            // Insertion ordered so that tasks queued for the same instant fire in the order they
            // were offered, which makes tests reproducible.
            buckets.add(new LinkedHashSet<>());
        }
        this.currentTime = floor(startTime);
    }

    /**
     * Parks an entry in whichever level can hold it, creating levels as needed.
     * 
     * @return false if the entry is already due, in which case it was not stored
     */
    boolean add(TimerEntry entry) {
        if (entry.isCancelled()) {
            return false;
        }
        long expiration = entry.getExpiration();
        if (expiration < currentTime + tickDuration) {
            return false;
        }
        if (expiration < currentTime + interval) {
            int index = (int) ((expiration / tickDuration) % wheelSize);
            buckets.get(index).add(entry);
            return true;
        }
        if (overflow == null) {
            // The level above ticks once per full revolution of this one, and starts from the same
            // instant so the two clocks never disagree about which tick they are in.
            overflow = new TimingWheel(interval, wheelSize, currentTime);
        }
        return overflow.add(entry);
    }

    /**
     * Moves this level's clock, and every level above it, to the given time. Slots crossed are
     * remembered for {@link #flushExpired(Consumer)} rather than emptied here.
     */
    void advanceClock(long now) {
        if (now < currentTime + tickDuration) {
            pendingFrom = pendingTo = -1L;
        } else {
            long newTime = floor(now);
            pendingFrom = currentTime + tickDuration;
            pendingTo = newTime;
            // A gap wider than one revolution would otherwise walk slots that have already been
            // walked. Every slot holds something due in the past by then, so one lap covers it.
            if (newTime - currentTime >= interval) {
                pendingFrom = newTime - interval + tickDuration;
            }
            currentTime = newTime;
        }
        if (overflow != null) {
            overflow.advanceClock(now);
        }
    }

    /**
     * Empties the slots crossed by the last clock move, highest level first, handing every entry to
     * the sink. The sink re-adds the entry from the bottom of the wheel: entries from a level above
     * settle into a lower one, and entries that are genuinely due fail to be re-added and are
     * reported as expired.
     */
    void flushExpired(Consumer<TimerEntry> sink) {
        if (overflow != null) {
            overflow.flushExpired(sink);
        }
        if (pendingFrom < 0) {
            return;
        }
        for (long t = pendingFrom; t <= pendingTo; t += tickDuration) {
            int index = (int) ((t / tickDuration) % wheelSize);
            Set<TimerEntry> bucket = buckets.get(index);
            if (bucket.isEmpty()) {
                continue;
            }
            List<TimerEntry> drained = new ArrayList<>(bucket);
            bucket.clear();
            for (TimerEntry entry : drained) {
                if (!entry.isCancelled()) {
                    sink.accept(entry);
                }
            }
        }
        pendingFrom = pendingTo = -1L;
    }

    /**
     * How many live entries this level and the ones above it hold.
     */
    int size() {
        int count = 0;
        for (Set<TimerEntry> bucket : buckets) {
            for (TimerEntry entry : bucket) {
                if (!entry.isCancelled()) {
                    count++;
                }
            }
        }
        return overflow != null ? count + overflow.size() : count;
    }

    /**
     * Discards every entry, keeping the clocks where they are.
     */
    void clear() {
        for (Set<TimerEntry> bucket : buckets) {
            bucket.clear();
        }
        pendingFrom = pendingTo = -1L;
        if (overflow != null) {
            overflow.clear();
        }
    }

    long getCurrentTime() {
        return currentTime;
    }

    long getTickDuration() {
        return tickDuration;
    }

    /**
     * How many levels exist, this one included. Exposed for tests that check the wheel grows only
     * as deep as the fire times require.
     */
    int levelCount() {
        return overflow != null ? 1 + overflow.levelCount() : 1;
    }

    private long floor(long time) {
        long remainder = time % tickDuration;
        // Math.floorMod semantics, so that pre-epoch times round down rather than towards zero.
        if (remainder < 0) {
            remainder += tickDuration;
        }
        return time - remainder;
    }

    /**
     * 
     * A task parked in the wheel. Cancellation is a flag rather than a removal: finding the slot an
     * entry sits in would mean either a back-reference to keep in step with every cascade, or a
     * scan of the whole wheel. Flagged entries are skipped when their slot is flushed, and the
     * queue's index keeps at most one entry per task, so the flags cannot pile up.
     * 
     * @Description: TimerEntry
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    static class TimerEntry {

        private final TaskId taskId;
        private final long expiration;
        private volatile boolean cancelled;

        TimerEntry(TaskId taskId, long expiration) {
            this.taskId = taskId;
            this.expiration = expiration;
        }

        TaskId getTaskId() {
            return taskId;
        }

        long getExpiration() {
            return expiration;
        }

        boolean isCancelled() {
            return cancelled;
        }

        void cancel() {
            this.cancelled = true;
        }

        @Override
        public String toString() {
            return taskId + "@" + expiration + (cancelled ? " (cancelled)" : "");
        }

    }

}
