package com.github.cronsmith.springapp.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chaconneai.openspreader.cluster.SelfRegisteringListener;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.GossipListener;
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
 * Wraps any {@link TaskManager} to make it cluster-aware, without the delegate knowing anything about
 * the cluster.
 *
 * <ul>
 * <li><b>Reads</b> are served from the local delegate — always.</li>
 * <li><b>Writes</b> are routed to the leader: the leader applies them to its store; a follower
 * forwards the call over the {@code cronsmith.taskmanager} channel and waits for the result.</li>
 * <li>When the storage is <b>replicated</b> (node-local), the leader also broadcasts each committed
 * write so every node — co-located or not — replays it onto its own independent copy. Each node keeps
 * a separate store and applies the write exactly once, so there is no double-write and a new leader
 * elected after a failover already holds the full state.</li>
 * </ul>
 *
 * A genuinely <b>shared</b> store (MySQL/PostgreSQL) is never replicated — it takes the cluster-wide
 * CAS path instead and no broadcast happens.
 *
 * @Description: ClusterTaskManager
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class ClusterTaskManager
        implements MultipleStoreTaskManager, GossipListener, SelfRegisteringListener {

    public static final String CHANNEL = "cronsmith.taskmanager";

    private static final Logger log = LoggerFactory.getLogger(ClusterTaskManager.class);

    private final TaskManager delegate;
    private final GossipCluster cluster;
    private final ObjectCodec codec;
    private final StoreType storeType;
    private final boolean replicated;
    private final long requestTimeoutMillis;

    private final ConcurrentHashMap<String, CompletableFuture<ClusterMessage>> pending =
            new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public ClusterTaskManager(TaskManager delegate, GossipCluster cluster, ObjectCodec codec,
            StoreType storeType, boolean replicated, long requestTimeoutMillis) {
        this.delegate = delegate;
        this.cluster = cluster;
        this.codec = codec;
        this.storeType = storeType;
        this.replicated = replicated;
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    @Override
    public StoreType getStoreType() {
        return storeType;
    }

    /** Claim the channel. Call once, after the cluster has started. */
    public void start() {
        cluster.addListener(CHANNEL, this);
    }

    // ------------------------------------------------------------------ writes

    @Override
    public TaskDetail saveTask(Task task, String initialParameter) throws TaskException {
        return (TaskDetail) write(ClusterOp.SAVE_TASK, task, initialParameter);
    }

    @Override
    public TaskDetail removeTask(TaskId taskId) throws TaskException {
        return (TaskDetail) write(ClusterOp.REMOVE_TASK, taskId);
    }

    @Override
    public LocalDateTime computeNextFiredDateTime(TaskId taskId, LocalDateTime previousFiredDateTime)
            throws TaskException {
        return (LocalDateTime) write(ClusterOp.COMPUTE_NEXT_FIRED, taskId, previousFiredDateTime);
    }

    @Override
    public boolean setTaskStatus(TaskId taskId, TaskStatus status) throws TaskException {
        return (Boolean) write(ClusterOp.SET_STATUS, taskId, status);
    }

    @Override
    public boolean compareAndSetTaskStatus(TaskId taskId, TaskStatus expected, TaskStatus target)
            throws TaskException {
        return (Boolean) write(ClusterOp.CAS_STATUS, taskId, expected, target);
    }

    @Override
    public void recordExecution(TaskExecutionLog executionLog) throws TaskException {
        write(ClusterOp.RECORD_EXECUTION, executionLog);
    }

    @Override
    public void recordMisfire(TaskId taskId, LocalDateTime missedDateTime) throws TaskException {
        write(ClusterOp.RECORD_MISFIRE, taskId, missedDateTime);
    }

    // ------------------------------------------------------------------ reads (local)

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
    public List<TaskDetail> findTaskDetails(TaskQuery query) throws TaskException {
        return delegate.findTaskDetails(query);
    }

    @Override
    public List<LocalDateTime> findNextFiredDateTimes(TaskId taskId, LocalDateTime startDateTime,
            LocalDateTime endDateTime) throws TaskException {
        return delegate.findNextFiredDateTimes(taskId, startDateTime, endDateTime);
    }

    @Override
    public List<TaskId> findUpcomingTasksBetween(LocalDateTime startDateTime,
            LocalDateTime endDateTime) throws TaskException {
        return delegate.findUpcomingTasksBetween(startDateTime, endDateTime);
    }

    @Override
    public List<TaskExecutionLog> findExecutionLogs(TaskId taskId, int limit, int offset)
            throws TaskException {
        return delegate.findExecutionLogs(taskId, limit, offset);
    }

    // restoreTasks is a startup step run by the scheduler on the leader; it reads the local store.
    @Override
    public void restoreTasks(TaskRestoreHandler restoreHandler) throws TaskException {
        delegate.restoreTasks(restoreHandler);
    }

    @Override
    public void close() throws TaskException {
        delegate.close();
    }

    // ------------------------------------------------------------------ routing

    private Object write(ClusterOp op, Object... args) throws TaskException {
        if (cluster.isLeader()) {
            Object result = apply(op, args);
            if (replicated) {
                broadcast(op, args);
            }
            return result;
        }
        return forwardToLeader(op, args);
    }

    private Object apply(ClusterOp op, Object[] a) throws TaskException {
        switch (op) {
            case SAVE_TASK:
                return delegate.saveTask((Task) a[0], (String) a[1]);
            case REMOVE_TASK:
                return delegate.removeTask((TaskId) a[0]);
            case COMPUTE_NEXT_FIRED:
                return delegate.computeNextFiredDateTime((TaskId) a[0], (LocalDateTime) a[1]);
            case SET_STATUS:
                return delegate.setTaskStatus((TaskId) a[0], (TaskStatus) a[1]);
            case CAS_STATUS:
                return delegate.compareAndSetTaskStatus((TaskId) a[0], (TaskStatus) a[1],
                        (TaskStatus) a[2]);
            case RECORD_EXECUTION:
                delegate.recordExecution((TaskExecutionLog) a[0]);
                return null;
            case RECORD_MISFIRE:
                delegate.recordMisfire((TaskId) a[0], (LocalDateTime) a[1]);
                return null;
            default:
                throw new TaskException("Unknown cluster op " + op);
        }
    }

    private void broadcast(ClusterOp op, Object[] args) {
        // Leader has already applied locally; multicast to everyone else (exclude self).
        cluster.multicastOn(CHANNEL, null, codec.encode(ClusterMessage.apply(op, args)), false);
    }

    private Object forwardToLeader(ClusterOp op, Object[] args) throws TaskException {
        String requestId = cluster.self().id() + "-" + sequence.incrementAndGet();
        CompletableFuture<ClusterMessage> future = new CompletableFuture<>();
        pending.put(requestId, future);
        try {
            byte[] payload = codec.encode(ClusterMessage.write(requestId, op, args));
            if (!cluster.sendToLeaderOn(CHANNEL, payload)) {
                throw new TaskException("No leader available to route write " + op);
            }
            ClusterMessage response = future.get(requestTimeoutMillis, TimeUnit.MILLISECONDS);
            if (response.error() != null) {
                throw new TaskException("Leader failed to apply " + op + ": " + response.error());
            }
            return response.result();
        } catch (TaskException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskException("Cluster write " + op + " failed", e);
        } finally {
            pending.remove(requestId);
        }
    }

    // ------------------------------------------------------------------ channel

    @Override
    public void onPayload(Node sender, byte[] content) {
        ClusterMessage message = (ClusterMessage) codec.decode(content, ClusterMessage.class);
        switch (message.type()) {
            case WRITE:
                handleWrite(sender, message);
                break;
            case RESPONSE:
                handleResponse(message);
                break;
            case APPLY:
                handleApply(sender, message);
                break;
            default:
                break;
        }
    }

    private void handleWrite(Node sender, ClusterMessage message) {
        Object result = null;
        String error = null;
        try {
            result = apply(message.op(), message.args());
            if (replicated) {
                broadcast(message.op(), message.args());
            }
        } catch (Exception e) {
            error = String.valueOf(e);
            log.warn("Failed to apply forwarded write {}", message.op(), e);
        }
        byte[] payload = codec.encode(ClusterMessage.response(message.requestId(), result, error));
        cluster.unicastOn(CHANNEL, sender, payload);
    }

    private void handleResponse(ClusterMessage message) {
        CompletableFuture<ClusterMessage> future = pending.remove(message.requestId());
        if (future != null) {
            future.complete(message);
        }
    }

    private void handleApply(Node sender, ClusterMessage message) {
        // Node-local storage is independent per node (each node keeps its own copy). Every node -
        // co-located or not - replays the leader's broadcast onto its own store; applied exactly once
        // per node, so there is no double-write.
        try {
            apply(message.op(), message.args());
        } catch (Exception e) {
            log.warn("Failed to apply broadcast write {}", message.op(), e);
        }
    }

}
