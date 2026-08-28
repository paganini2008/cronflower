package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskException;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskRestoreHandler;
import com.github.cronsmith.springapp.scheduler.TaskStatus;

/**
 * Wraps the cluster {@link TaskManager} so a node's scheduler only ever picks up the task groups that
 * hash to it. Used solely by the sharded scheduler — the CRUD/UI side keeps using the undecorated
 * manager and still sees every task.
 *
 * <p>
 * Only the three paths a scheduler acquires work through are filtered:
 * <ul>
 * <li>{@link #findUpcomingTasksBetween} — the windowed claim;</li>
 * <li>{@link #restoreTasks} — non-windowed start-up restore;</li>
 * <li>{@link #findTaskDetails} — the {@code RUNNING}-recovery scan at start.</li>
 * </ul>
 * plus a guard on the fire transition: {@link #compareAndSetTaskStatus} from {@code SCHEDULED} to
 * {@code RUNNING} is refused for a group this node no longer owns, so a task that was parked here
 * before a re-shard does not run here — it already left this wheel when it came due, and the new owner
 * claims it (past its fire time → misfire policy). This is the lazy release; the shared store's atomic
 * CAS still guarantees at most one node runs any given occurrence.
 *
 * <p>
 * Everything else delegates verbatim.
 *
 * @Description: ShardingTaskManager
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public class ShardingTaskManager implements TaskManager {

    private final TaskManager delegate;
    private final GroupShardingStrategy sharding;

    public ShardingTaskManager(TaskManager delegate, GroupShardingStrategy sharding) {
        this.delegate = delegate;
        this.sharding = sharding;
    }

    // ---------------------------------------------------------------- filtered acquisition

    @Override
    public List<TaskId> findUpcomingTasksBetween(LocalDateTime startDateTime,
            LocalDateTime endDateTime) throws TaskException {
        List<TaskId> all = delegate.findUpcomingTasksBetween(startDateTime, endDateTime);
        List<TaskId> mine = new ArrayList<>(all.size());
        for (TaskId taskId : all) {
            if (sharding.owns(taskId)) {
                mine.add(taskId);
            }
        }
        return mine;
    }

    @Override
    public void restoreTasks(TaskRestoreHandler restoreHandler) throws TaskException {
        delegate.restoreTasks((taskId, nextFiredDateTime) -> {
            if (sharding.owns(taskId)) {
                restoreHandler.onRestore(taskId, nextFiredDateTime);
            }
        });
    }

    @Override
    public List<TaskDetail> findTaskDetails(TaskQuery query) throws TaskException {
        List<TaskDetail> all = delegate.findTaskDetails(query);
        List<TaskDetail> mine = new ArrayList<>(all.size());
        for (TaskDetail detail : all) {
            if (sharding.owns(detail.getTaskId())) {
                mine.add(detail);
            }
        }
        return mine;
    }

    // ---------------------------------------------------------------- fire guard

    @Override
    public boolean compareAndSetTaskStatus(TaskId taskId, TaskStatus expected, TaskStatus target)
            throws TaskException {
        if (expected == TaskStatus.SCHEDULED && target == TaskStatus.RUNNING
                && !sharding.owns(taskId)) {
            // Parked here before a re-shard, no longer ours: refuse the fire so the new owner runs it.
            return false;
        }
        return delegate.compareAndSetTaskStatus(taskId, expected, target);
    }

    // ---------------------------------------------------------------- straight delegation

    @Override
    public TaskDetail saveTask(Task task, String initialParameter) throws TaskException {
        return delegate.saveTask(task, initialParameter);
    }

    @Override
    public TaskDetail removeTask(TaskId taskId) throws TaskException {
        return delegate.removeTask(taskId);
    }

    @Override
    public TaskDetail getTaskDetail(TaskId taskId, boolean thrown) throws TaskException {
        return delegate.getTaskDetail(taskId, thrown);
    }

    @Override
    public boolean hasTask(TaskId taskId) throws TaskException {
        return delegate.hasTask(taskId);
    }

    @Override
    public int getTaskCount(TaskQuery query) throws TaskException {
        return delegate.getTaskCount(query);
    }

    @Override
    public List<LocalDateTime> findNextFiredDateTimes(TaskId taskId, LocalDateTime startDateTime,
            LocalDateTime endDateTime) throws TaskException {
        return delegate.findNextFiredDateTimes(taskId, startDateTime, endDateTime);
    }

    @Override
    public LocalDateTime computeNextFiredDateTime(TaskId taskId, LocalDateTime previousFiredDateTime)
            throws TaskException {
        return delegate.computeNextFiredDateTime(taskId, previousFiredDateTime);
    }

    @Override
    public boolean setTaskStatus(TaskId taskId, TaskStatus status) throws TaskException {
        return delegate.setTaskStatus(taskId, status);
    }

    @Override
    public void recordExecution(TaskExecutionLog executionLog) throws TaskException {
        delegate.recordExecution(executionLog);
    }

    @Override
    public List<TaskExecutionLog> findExecutionLogs(TaskId taskId, int limit, int offset)
            throws TaskException {
        return delegate.findExecutionLogs(taskId, limit, offset);
    }

    @Override
    public void recordMisfire(TaskId taskId, LocalDateTime missedDateTime) throws TaskException {
        delegate.recordMisfire(taskId, missedDateTime);
    }

    @Override
    public void close() throws TaskException {
        delegate.close();
    }

}
