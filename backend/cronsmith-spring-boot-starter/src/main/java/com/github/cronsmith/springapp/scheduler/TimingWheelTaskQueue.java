package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import com.github.cronsmith.springapp.scheduler.TimingWheel.TimerEntry;

/**
 * 
 * An {@link UpcomingTaskQueue} backed by a hierarchical timing wheel.
 * 
 * <p>
 * Adding, cancelling and firing are all constant time regardless of how far ahead a task is due, so
 * a schedule mixing per-second jobs with yearly ones costs no more than the per-second ones alone.
 * 
 * <p>
 * One lock covers the whole wheel. Contention is not a concern here: the clock thread takes it once
 * per tick, and workers take it once per task they re-park, so it is held for microseconds at a
 * time and never while a task body runs.
 * 
 * @Description: TimingWheelTaskQueue
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TimingWheelTaskQueue implements UpcomingTaskQueue {

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The live entry per task, so that re-parking a task supersedes its previous fire time instead
     * of firing it twice.
     */
    private final Map<TaskId, TimerEntry> index = new HashMap<>();

    private final TimingWheel root;
    private final ZoneId zoneId;

    public TimingWheelTaskQueue() {
        this(Settings.DEFAULT_TICK_DURATION, Settings.DEFAULT_WHEEL_SIZE, Settings.DEFAULT_ZONE_ID,
                Settings.now());
    }

    public TimingWheelTaskQueue(long tickDuration, int wheelSize) {
        this(tickDuration, wheelSize, Settings.DEFAULT_ZONE_ID, Settings.now());
    }

    /**
     * @param tickDuration milliseconds per slot of the lowest level, and therefore the resolution
     *        of the whole queue
     * @param wheelSize slots per level
     * @param zoneId the zone the {@link LocalDateTime} arguments are read in
     * @param startTime where the clock starts
     */
    public TimingWheelTaskQueue(long tickDuration, int wheelSize, ZoneId zoneId,
            LocalDateTime startTime) {
        this.zoneId = zoneId;
        this.root = new TimingWheel(tickDuration, wheelSize, toMillis(startTime, zoneId));
    }

    @Override
    public boolean offer(LocalDateTime firedDateTime, TaskId taskId) {
        if (firedDateTime == null || taskId == null) {
            return false;
        }
        long expiration = toMillis(firedDateTime, zoneId);
        lock.lock();
        try {
            TimerEntry previous = index.remove(taskId);
            if (previous != null) {
                previous.cancel();
            }
            TimerEntry entry = new TimerEntry(taskId, expiration);
            if (!root.add(entry)) {
                return false;
            }
            index.put(taskId, entry);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Collection<TaskId> advance(LocalDateTime now) {
        if (now == null) {
            return Collections.emptyList();
        }
        long nowMillis = toMillis(now, zoneId);
        List<TaskId> expired = new ArrayList<>();
        lock.lock();
        try {
            // Two passes: every level's clock first, then the slots they crossed. See TimingWheel.
            root.advanceClock(nowMillis);
            root.flushExpired(entry -> {
                // Re-adding from the root is what cascades an entry down a level. Only an entry
                // that no level will take is genuinely due.
                if (!root.add(entry)) {
                    if (index.remove(entry.getTaskId()) != null) {
                        expired.add(entry.getTaskId());
                    }
                }
            });
        } finally {
            lock.unlock();
        }
        return expired;
    }

    @Override
    public boolean remove(TaskId taskId) {
        if (taskId == null) {
            return false;
        }
        lock.lock();
        try {
            TimerEntry entry = index.remove(taskId);
            if (entry != null) {
                entry.cancel();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(TaskId taskId) {
        lock.lock();
        try {
            return taskId != null && index.containsKey(taskId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return index.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            index.clear();
            root.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many levels the wheel has grown to. Exposed for tests and diagnostics.
     */
    public int getLevelCount() {
        lock.lock();
        try {
            return root.levelCount();
        } finally {
            lock.unlock();
        }
    }

    private static long toMillis(LocalDateTime ldt, ZoneId zoneId) {
        return ldt.atZone(zoneId).toInstant().toEpochMilli();
    }

}
