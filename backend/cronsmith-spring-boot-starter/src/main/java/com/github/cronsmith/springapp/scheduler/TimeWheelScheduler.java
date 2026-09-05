package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * A clock-driven scheduler over a hierarchical timing wheel.
 * 
 * <p>
 * One thread advances the clock on a fixed tick. Everything that came due on that tick is handed
 * straight to the worker pool and the clock thread moves on; it never waits for a task, so a slow
 * task delays nothing but itself. Each run schedules the next one when it finishes, so a task
 * occupies exactly one slot in the wheel at a time.
 * 
 * <p>
 * A tick that arrives late does not lose work: the wheel is advanced to the current time and yields
 * everything it passed over, including whole ticks that were skipped. Fire times older than
 * {@link #setMisfireThreshold(long) the misfire threshold} are handled by the task's
 * {@link MisfirePolicy} instead of being run as though nothing happened.
 * 
 * <p>
 * State lives in the {@link TaskManager}, not here. Restarting against a durable task manager
 * resumes whatever was outstanding; restarting against the in-memory one starts empty.
 * 
 * @Description: TimeWheelScheduler
 * @Author: Fred Feng
 * @Date: 30/03/2025
 * @Version 1.0.0
 */
public class TimeWheelScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TimeWheelScheduler.class);

    private final ScheduledExecutorService schedulerThreads;
    private final ExecutorService workerThreads;
    private final ExecutorServiceFactory executorServiceFactory;
    private final AtomicBoolean started = new AtomicBoolean();
    private final List<TaskListener> taskListeners = new CopyOnWriteArrayList<>();

    private TaskManager taskManager = new InMemoryTaskManager();
    // Built on first use by queue(), so the wheel gets the configured tick and a start time of
    // "now"; a queue set explicitly through setTaskQueue() is used as-is instead.
    private UpcomingTaskQueue taskQueue;
    private ZoneId zoneId = Settings.DEFAULT_ZONE_ID;
    private ErrorHandler errorHandler = new LoggingErrorHandler();
    private long tickDuration = Settings.DEFAULT_TICK_DURATION;
    private long misfireThreshold = Settings.DEFAULT_MISFIRE_THRESHOLD;
    // 0 = load every active task into the wheel at start (the single-node default). > 0 = only hold
    // tasks whose next fire is within this many milliseconds, claimed from the store on an interval,
    // so a very large schedule needs only a bounded slice in memory and failover is quick.
    private long claimWindowMillis = 0L;
    private long claimIntervalMillis = Settings.DEFAULT_CLAIM_INTERVAL_MILLIS;

    private volatile ScheduledFuture<?> tickFuture;
    private volatile ScheduledFuture<?> claimFuture;
    private volatile TaskInvoker taskInvoker;

    public TimeWheelScheduler() {
        this(new DefaultExecutorServiceFactory());
    }

    public TimeWheelScheduler(ExecutorServiceFactory executorServiceFactory) {
        this.executorServiceFactory = executorServiceFactory;
        this.schedulerThreads = executorServiceFactory.getSchedulerThreads();
        this.workerThreads = executorServiceFactory.getWorkerThreads();
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public void setTaskManager(TaskManager taskManager) {
        checkNotStarted();
        this.taskManager = taskManager;
    }

    public UpcomingTaskQueue getTaskQueue() {
        return queue();
    }

    public void setTaskQueue(UpcomingTaskQueue taskQueue) {
        checkNotStarted();
        this.taskQueue = taskQueue;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        checkNotStarted();
        this.zoneId = zoneId;
    }

    public List<TaskListener> getTaskListeners() {
        return taskListeners;
    }

    public void addTaskListener(TaskListener taskListener) {
        if (taskListener != null) {
            taskListeners.add(taskListener);
        }
    }

    public void removeTaskListener(TaskListener taskListener) {
        taskListeners.remove(taskListener);
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * How often the clock advances, in milliseconds. Also the finest resolution a task can be
     * scheduled at. Must be set before starting.
     */
    public void setTickDuration(long tickDuration) {
        checkNotStarted();
        if (tickDuration <= 0) {
            throw new IllegalArgumentException("Tick duration must be positive: " + tickDuration);
        }
        this.tickDuration = tickDuration;
    }

    public long getTickDuration() {
        return tickDuration;
    }

    /**
     * How late a fire time may be, in milliseconds, before the task's {@link MisfirePolicy}
     * decides what happens instead of it simply being run.
     */
    public void setMisfireThreshold(long misfireThreshold) {
        this.misfireThreshold = misfireThreshold;
    }

    public long getMisfireThreshold() {
        return misfireThreshold;
    }

    /**
     * How far ahead, in milliseconds, to hold tasks in the wheel. {@code 0} (the default) loads every
     * active task at start and keeps each in the wheel across runs. A positive value switches on
     * windowed loading: only tasks due within the window are parked, the rest are left in the store
     * and claimed as they come due, so total task count no longer bounds memory or restart time.
     */
    public void setClaimWindow(long claimWindowMillis) {
        this.claimWindowMillis = Math.max(0L, claimWindowMillis);
    }

    public long getClaimWindow() {
        return claimWindowMillis;
    }

    /** How often, in milliseconds, to claim newly-due tasks from the store when windowed. */
    public void setClaimInterval(long claimIntervalMillis) {
        if (claimIntervalMillis <= 0) {
            throw new IllegalArgumentException("Claim interval must be positive");
        }
        this.claimIntervalMillis = claimIntervalMillis;
    }

    public long getClaimInterval() {
        return claimIntervalMillis;
    }

    public boolean isStarted() {
        return started.get();
    }

    /**
     * Runs one windowed claim immediately, on top of the periodic one, so an external change — a
     * cluster re-sharding, say — is picked up at once rather than at the next claim interval.
     * Submitted to the scheduler's own executor, so it is serialized with the periodic claim and the
     * tick and adds no new concurrency. A no-op when the scheduler is stopped or not windowed.
     */
    public void claimNow() {
        if (started.get() && claimWindowMillis > 0) {
            schedulerThreads.execute(this::claim);
        }
    }

    /**
     * Registers a task and queues its first run. Re-registering a task that is already scheduled
     * does nothing; one that has finished or been canceled is registered afresh.
     * 
     * @return whether the task is now waiting for a fire time
     */
    public boolean schedule(Task task, String initialParameter) {
        if (task == null) {
            throw new IllegalArgumentException("Task is required");
        }
        TaskId taskId = task.getTaskId();
        TaskStatus status = taskManager.getTaskStatus(taskId);
        if (status == null || status.isTerminal()) {
            taskManager.saveTask(task, initialParameter);
            status = TaskStatus.STANDBY;
        }
        return status == TaskStatus.STANDBY && scheduleNext(taskId, now());
    }

    public boolean schedule(Task task) {
        return schedule(task, null);
    }

    /**
     * Queues the next run of a task that is already registered and standing by.
     */
    public boolean schedule(TaskId taskId) {
        return taskManager.getTaskStatus(taskId) == TaskStatus.STANDBY
                && scheduleNext(taskId, now());
    }

    /**
     * Holds a task back. It keeps its stored state but will not fire until resumed. A run already
     * under way is left to finish.
     */
    public boolean pause(TaskId taskId) {
        if (taskManager.setTaskStatus(taskId, TaskStatus.PAUSED)) {
            queue().remove(taskId);
            return true;
        }
        return false;
    }

    /**
     * Puts a paused task back in the wheel, at its next fire time from now.
     */
    public boolean resume(TaskId taskId) {
        if (taskManager.getTaskStatus(taskId) != TaskStatus.PAUSED) {
            return false;
        }
        return taskManager.setTaskStatus(taskId, TaskStatus.STANDBY)
                && scheduleNext(taskId, now());
    }

    /**
     * Withdraws a task. It stays in the task manager, in a terminal state, so its history can still
     * be read; use {@link #remove(TaskId)} to delete it.
     */
    public boolean cancel(TaskId taskId) {
        if (!taskManager.setTaskStatus(taskId, TaskStatus.CANCELED)) {
            return false;
        }
        queue().remove(taskId);
        TaskDetail taskDetail = taskManager.getTaskDetail(taskId, false);
        if (taskDetail != null) {
            notifyListeners(l -> l.onTaskCanceled(taskDetail));
        }
        return true;
    }

    /**
     * Deletes a task and everything stored for it.
     */
    public TaskDetail remove(TaskId taskId) {
        queue().remove(taskId);
        return taskManager.removeTask(taskId);
    }

    /**
     * Starts the clock and restores whatever the task manager was still holding.
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        taskInvoker = new TaskInvoker(taskManager, workerThreads, taskListeners, errorHandler);
        if (claimWindowMillis > 0) {
            // Windowed: recover anything left mid-run, then claim due tasks on an interval instead
            // of loading the whole schedule.
            recoverRunningTasks();
            claimFuture = schedulerThreads.scheduleAtFixedRate(this::claim, 0L, claimIntervalMillis,
                    TimeUnit.MILLISECONDS);
            log.info("TimeWheelScheduler started (windowed: {}ms window, {}ms claim, {}ms tick).",
                    claimWindowMillis, claimIntervalMillis, tickDuration);
        } else {
            restoreTasks();
            log.info("TimeWheelScheduler is started with a {}ms tick.", tickDuration);
        }
        tickFuture = schedulerThreads.scheduleAtFixedRate(this::tick, tickDuration, tickDuration,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the clock. Tasks already handed to workers are left to finish; the pools are shut down
     * only if this scheduler owns them.
     */
    @Override
    public void close() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        ScheduledFuture<?> future = tickFuture;
        if (future != null) {
            future.cancel(false);
            tickFuture = null;
        }
        ScheduledFuture<?> claim = claimFuture;
        if (claim != null) {
            claim.cancel(false);
            claimFuture = null;
        }
        queue().clear();
        if (executorServiceFactory.isAutoClosed()) {
            executorServiceFactory.shutdown(workerThreads);
            executorServiceFactory.shutdown(schedulerThreads);
        }
        log.info("TimeWheelScheduler is closed.");
    }

    /**
     * One turn of the clock. Runs on the scheduler thread and must not block: everything it finds
     * is handed to a worker and the method returns.
     */
    private void tick() {
        try {
            LocalDateTime now = now();
            Collection<TaskId> dueTaskIds = queue().advance(now);
            if (dueTaskIds.isEmpty()) {
                return;
            }
            if (log.isTraceEnabled()) {
                log.trace("Tick at {} released {} task(s), {} still waiting.", now,
                        dueTaskIds.size(), queue().size());
            }
            for (TaskId taskId : dueTaskIds) {
                dispatch(taskId, now);
            }
        } catch (Throwable e) {
            // The clock has to survive anything a single tick throws, or one bad task would stop
            // every other task in the process.
            errorHandler.onHandleScheduler(e);
        }
    }

    private void dispatch(TaskId taskId, LocalDateTime tickTime) {
        try {
            workerThreads.execute(() -> fire(taskId, tickTime));
        } catch (RejectedExecutionException e) {
            // The pool is saturated or shutting down. Put the task back rather than dropping it,
            // unless we are on the way out.
            if (started.get()) {
                errorHandler.onHandleScheduler(e);
                scheduleNext(taskId, tickTime);
            }
        }
    }

    /**
     * Runs one occurrence, then queues the one after it. Runs on a worker thread.
     */
    private void fire(TaskId taskId, LocalDateTime tickTime) {
        TaskDetail taskDetail = taskManager.getTaskDetail(taskId, false);
        if (taskDetail == null || taskDetail.isUnavailable()) {
            return;
        }
        LocalDateTime scheduledDateTime = taskDetail.getNextFiredDateTime() != null
                ? taskDetail.getNextFiredDateTime()
                : tickTime;
        if (isMisfire(scheduledDateTime, tickTime)) {
            if (!handleMisfire(taskDetail, scheduledDateTime, tickTime)) {
                return;
            }
        }
        // Only a task the wheel released may start, and only once: whoever wins this transition
        // owns the run, and a concurrent pause or cancel makes it fail outright.
        if (!taskManager.compareAndSetTaskStatus(taskId, TaskStatus.SCHEDULED,
                TaskStatus.RUNNING)) {
            return;
        }
        notifyListeners(l -> l.onTaskTriggered(scheduledDateTime, taskDetail));
        try {
            taskInvoker.invoke(taskDetail, scheduledDateTime);
        } catch (Throwable e) {
            errorHandler.onHandleTask(scheduledDateTime, e);
        } finally {
            rescheduleAfterRun(taskId, scheduledDateTime);
        }
    }

    private boolean isMisfire(LocalDateTime scheduledDateTime, LocalDateTime tickTime) {
        if (misfireThreshold <= 0) {
            return false;
        }
        long lateness = toMillis(tickTime) - toMillis(scheduledDateTime);
        return lateness > misfireThreshold;
    }

    /**
     * @return whether the occurrence should still be run
     */
    private boolean handleMisfire(TaskDetail taskDetail, LocalDateTime scheduledDateTime,
            LocalDateTime tickTime) {
        TaskId taskId = taskDetail.getTaskId();
        taskManager.recordMisfire(taskId, scheduledDateTime);
        notifyListeners(l -> l.onTaskMisfired(scheduledDateTime, taskDetail));
        MisfirePolicy policy = taskDetail.getTask().getMisfirePolicy();
        if (log.isDebugEnabled()) {
            log.debug("Task {} missed its fire time {} (now {}), applying {}.", taskId,
                    scheduledDateTime, tickTime, policy);
        }
        if (policy == MisfirePolicy.SKIP) {
            // Drop this occurrence and line up the first one that is still in the future.
            scheduleNext(taskId, tickTime);
            return false;
        }
        // FIRE_ONCE_NOW and FIRE_ALL both run this occurrence; they differ in how the ones after
        // it are chosen, which scheduleNext() decides.
        return true;
    }

    private void rescheduleAfterRun(TaskId taskId, LocalDateTime previousFiredDateTime) {
        TaskStatus status = taskManager.getTaskStatus(taskId);
        if (status == null || status.isTerminal() || status == TaskStatus.PAUSED) {
            // Canceled or paused while it was running: leave it alone.
            return;
        }
        scheduleNext(taskId, previousFiredDateTime);
    }

    /**
     * Works out the next fire time after the given point and parks the task on it.
     * 
     * @return whether the task is now waiting in the wheel
     */
    private boolean scheduleNext(TaskId taskId, LocalDateTime from) {
        LocalDateTime cursor = from;
        for (int step = 0; step < Settings.DEFAULT_MAX_CATCH_UP_STEPS; step++) {
            LocalDateTime nextFiredDateTime;
            try {
                nextFiredDateTime = taskManager.computeNextFiredDateTime(taskId, cursor);
            } catch (Throwable e) {
                errorHandler.onHandleScheduler(e);
                return false;
            }
            if (nextFiredDateTime == null) {
                finish(taskId);
                return false;
            }
            if (claimWindowMillis > 0 && nextFiredDateTime.isAfter(
                    now().plusNanos(TimeUnit.MILLISECONDS.toNanos(claimWindowMillis)))) {
                // Too far ahead to hold in the wheel. Its next fire time is already persisted, so
                // leave it standing by in the store; the claim loop parks it once it comes due.
                taskManager.setTaskStatus(taskId, TaskStatus.STANDBY);
                return false;
            }
            if (queue().offer(nextFiredDateTime, taskId)) {
                if (taskManager.setTaskStatus(taskId, TaskStatus.SCHEDULED)) {
                    TaskDetail taskDetail = taskManager.getTaskDetail(taskId, false);
                    if (taskDetail != null) {
                        notifyListeners(l -> l.onTaskScheduled(nextFiredDateTime, taskDetail));
                    }
                    return true;
                }
                // The task was paused or canceled between computing the time and storing the
                // status; take it back out of the wheel.
                queue().remove(taskId);
                return false;
            }
            // That time has already passed. What to do about it is the misfire policy's call.
            TaskDetail taskDetail = taskManager.getTaskDetail(taskId, false);
            MisfirePolicy policy = taskDetail != null ? taskDetail.getTask().getMisfirePolicy()
                    : MisfirePolicy.FIRE_ONCE_NOW;
            if (policy == MisfirePolicy.SKIP) {
                // Walk forward until a fire time lands in the future.
                cursor = nextFiredDateTime;
                continue;
            }
            // Catch up on this occurrence at the next tick, then carry on from there.
            LocalDateTime catchUp = now().plusNanos(TimeUnit.MILLISECONDS.toNanos(tickDuration));
            if (queue().offer(catchUp, taskId)) {
                taskManager.setTaskStatus(taskId, TaskStatus.SCHEDULED);
                return true;
            }
            cursor = nextFiredDateTime;
        }
        log.warn("Gave up catching task {} up after {} attempts; it produces fire times faster"
                + " than they can be scheduled.", taskId, Settings.DEFAULT_MAX_CATCH_UP_STEPS);
        taskManager.setTaskStatus(taskId, TaskStatus.STANDBY);
        return false;
    }

    private void finish(TaskId taskId) {
        if (taskManager.setTaskStatus(taskId, TaskStatus.FINISHED)) {
            TaskDetail taskDetail = taskManager.getTaskDetail(taskId, false);
            if (taskDetail != null) {
                notifyListeners(l -> l.onTaskFinished(taskDetail));
            }
        }
    }

    /**
     * Puts back whatever the task manager was still holding when this scheduler started.
     *
     * <p>
     * A stored fire time that is still ahead is re-parked as it stands, rather than recomputed from
     * the cron expression. That matters for two reasons: it does not consume an occurrence from a
     * one-shot expression that was already advanced before start, and it re-parks a task idempotently
     * if it was scheduled in this same session. Only a missing or already-past time falls through to
     * {@link #scheduleNext}, which recomputes and applies the misfire policy.
     */
    private void restoreTasks() {
        try {
            taskManager.restoreTasks(this::restoreOne);
        } catch (Throwable e) {
            errorHandler.onHandleScheduler(e);
        }
    }

    private void restoreOne(TaskId taskId, LocalDateTime storedNextFiredDateTime) {
        if (storedNextFiredDateTime == null) {
            // Never scheduled before: compute the first occurrence from the cron.
            scheduleNext(taskId, now());
            return;
        }
        if (storedNextFiredDateTime.isAfter(now())) {
            // The stored occurrence is still ahead: re-park it exactly as it stands, without
            // recomputing. Recomputing would consume an occurrence from a one-shot expression that
            // was already advanced before the scheduler stopped.
            if (queue().offer(storedNextFiredDateTime, taskId)
                    && taskManager.setTaskStatus(taskId, TaskStatus.SCHEDULED)) {
                TaskDetail taskDetail = taskManager.getTaskDetail(taskId, false);
                if (taskDetail != null) {
                    notifyListeners(l -> l.onTaskScheduled(storedNextFiredDateTime, taskDetail));
                }
            } else {
                queue().remove(taskId);
            }
            return;
        }
        // The stored occurrence is already in the past: it was missed while the scheduler was
        // down. Hand it to the misfire policy instead of recomputing from the cron, so a one-shot
        // still runs its single occurrence rather than being advanced straight past it.
        TaskDetail taskDetail = taskManager.getTaskDetail(taskId, false);
        MisfirePolicy policy = taskDetail != null ? taskDetail.getTask().getMisfirePolicy()
                : MisfirePolicy.FIRE_ONCE_NOW;
        taskManager.recordMisfire(taskId, storedNextFiredDateTime);
        if (taskDetail != null) {
            notifyListeners(l -> l.onTaskMisfired(storedNextFiredDateTime, taskDetail));
        }
        if (policy == MisfirePolicy.SKIP) {
            // Drop the missed occurrence and compute the next future one.
            scheduleNext(taskId, now());
            return;
        }
        // Run the missed occurrence at the next tick; what follows it is chosen after it runs.
        LocalDateTime catchUp = now().plusNanos(TimeUnit.MILLISECONDS.toNanos(tickDuration));
        if (queue().offer(catchUp, taskId)) {
            taskManager.setTaskStatus(taskId, TaskStatus.SCHEDULED);
        }
    }

    /**
     * Windowed start-up: a task left {@code RUNNING} was mid-run when the previous owner stopped.
     * Reset it and let {@link #restoreOne} apply its misfire policy to the occurrence it was on.
     */
    private void recoverRunningTasks() {
        try {
            List<TaskDetail> running =
                    taskManager.findTaskDetails(TaskQuery.newQuery().statuses(TaskStatus.RUNNING));
            for (TaskDetail detail : running) {
                TaskId taskId = detail.getTaskId();
                if (taskManager.setTaskStatus(taskId, TaskStatus.STANDBY)) {
                    restoreOne(taskId, detail.getNextFiredDateTime());
                }
            }
        } catch (Throwable e) {
            errorHandler.onHandleScheduler(e);
        }
    }

    /**
     * Windowed loading: park every task now due within the window that is not already in the wheel,
     * at its stored fire time. Runs on an interval, so a task far in the future costs nothing until
     * it comes due.
     */
    private void claim() {
        try {
            LocalDateTime now = now();
            LocalDateTime windowEnd =
                    now.plusNanos(TimeUnit.MILLISECONDS.toNanos(claimWindowMillis));
            // From the epoch, not from now: a task whose fire time has already slipped into the past
            // (claimed late, or missed while nothing owned the schedule) must still be picked up and
            // handed to its misfire policy, not stranded.
            LocalDateTime windowStart = LocalDateTime.of(1970, 1, 1, 0, 0);
            for (TaskId taskId : taskManager.findUpcomingTasksBetween(windowStart, windowEnd)) {
                if (queue().contains(taskId)) {
                    continue;
                }
                TaskDetail detail = taskManager.getTaskDetail(taskId, false);
                if (detail == null || detail.isUnavailable()
                        || detail.getTaskStatus() == TaskStatus.RUNNING) {
                    continue;
                }
                restoreOne(taskId, detail.getNextFiredDateTime());
            }
        } catch (Throwable e) {
            errorHandler.onHandleScheduler(e);
        }
    }

    private void notifyListeners(Consumer<TaskListener> action) {
        for (TaskListener listener : taskListeners) {
            try {
                action.accept(listener);
            } catch (Throwable e) {
                errorHandler.onHandleTaskResult(now(), e);
            }
        }
    }

    /**
     * The timing wheel, built on first use so it picks up the configured tick and starts its clock
     * at "now" rather than at whenever this scheduler object was constructed. A queue installed
     * through {@link #setTaskQueue(UpcomingTaskQueue)} is used instead of building one.
     */
    private synchronized UpcomingTaskQueue queue() {
        if (taskQueue == null) {
            taskQueue = new TimingWheelTaskQueue(tickDuration, Settings.DEFAULT_WHEEL_SIZE, zoneId,
                    now());
        }
        return taskQueue;
    }

    private void checkNotStarted() {
        if (started.get()) {
            throw new IllegalStateException("Cannot reconfigure a running scheduler");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(zoneId);
    }

    private long toMillis(LocalDateTime ldt) {
        return ldt.atZone(zoneId).toInstant().toEpochMilli();
    }

}
