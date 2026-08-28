package com.github.cronsmith.springapp.scheduler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.chaconneai.openspreader.cluster.SelfRegisteringListener;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.GossipListener;
import com.github.cronsmith.springapp.scheduler.TaskExecutionContext;
import com.github.cronsmith.springapp.scheduler.TaskInvocationException;
import com.github.cronsmith.springapp.scheduler.CronsmithServerProperties.Dispatch;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry.ExecutorInstance;

/**
 * Default {@link TaskDispatcher}: POSTs the run to a live executor, then blocks on a future keyed by
 * {@code executionId} that {@link #complete} resolves when the executor calls back.
 *
 * <p>
 * Only the leader dispatches, so the pending future lives on the leader. A {@code completeTask} that
 * arrives at a follower is forwarded to the leader over the {@code cronsmith.executions} channel.
 *
 * @Description: DefaultTaskDispatcher
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class DefaultTaskDispatcher
        implements TaskDispatcher, GossipListener, SelfRegisteringListener {

    public static final String CHANNEL = "cronsmith.executions";

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskDispatcher.class);

    private final ExecutorRegistry registry;
    private final GossipCluster cluster;
    private final ObjectCodec codec;
    private final long maxAwaitMillis;
    private final RestClient restClient;

    private final ConcurrentHashMap<String, CompletableFuture<CompleteRequest>> pending =
            new ConcurrentHashMap<>();

    public DefaultTaskDispatcher(ExecutorRegistry registry, GossipCluster cluster, ObjectCodec codec,
            Dispatch dispatch) {
        this.registry = registry;
        this.cluster = cluster;
        this.codec = codec;
        this.maxAwaitMillis = dispatch.getMaxAwaitMillis();
        SimpleClientHttpRequestFactory factory = HttpRequestFactories
                .create(dispatch.getConnectTimeoutMillis(), dispatch.getReadTimeoutMillis());
        this.restClient = RestClient.builder().requestFactory(factory).defaultHeaders(headers -> {
            if (dispatch.getHeaders() != null) {
                dispatch.getHeaders().forEach(headers::add);
            }
        }).build();
    }

    /** Claim the completion channel. Call once, after the cluster has started. */
    public void start() {
        cluster.addListener(CHANNEL, this);
    }

    @Override
    public Object dispatchAndWait(DispatchRequest request) {
        ExecutorInstance executor = registry.pick(request.application(), request.taskGroup())
                .orElseThrow(() -> new TaskInvocationException(
                        "No live executor for application '" + request.application() + "'"));
        String executionId = UUID.randomUUID().toString();
        // Tag the run with the dispatching node now, so even a run that never reports back (timeout)
        // still records which scheduler owned it.
        String schedulerRepr = schedulerRepr();
        TaskExecutionContext.current().ifPresent(l -> l.schedulerRepr(schedulerRepr));
        CompletableFuture<CompleteRequest> future = new CompletableFuture<>();
        pending.put(executionId, future);
        try {
            RunRequest run = new RunRequest(executionId, request.taskGroup(), request.taskName(),
                    request.className(), request.beanName(), request.methodName(),
                    request.initialParameter(), 0, request.timeout(), schedulerRepr);
            restClient.post().uri(executor.runUrl()).contentType(MediaType.APPLICATION_JSON)
                    .body(run).retrieve().toBodilessEntity();

            long await = request.timeout() > 0 ? request.timeout() : maxAwaitMillis;
            CompleteRequest result = future.get(await, TimeUnit.MILLISECONDS);
            // Record which executor actually ran it (self-reported), even if it reported a failure.
            TaskExecutionContext.current().ifPresent(l -> l.executorRepr(result.executorRepr()));
            if (!result.success()) {
                throw new TaskInvocationException("Executor reported failure for task "
                        + request.taskName() + firstLine(result.errorDetail()));
            }
            return result.returnValue();
        } catch (TimeoutException e) {
            throw new TaskInvocationException("Executor did not report back for task "
                    + request.taskName() + " within " + describeAwait(request), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskInvocationException("Interrupted while running task " + request.taskName(),
                    e);
        } catch (TaskInvocationException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskInvocationException(
                    "Failed to dispatch task " + request.taskName() + " to " + executor.runUrl(), e);
        } finally {
            pending.remove(executionId);
        }
    }

    @Override
    public void complete(CompleteRequest result) {
        CompletableFuture<CompleteRequest> future = pending.remove(result.executionId());
        if (future != null) {
            future.complete(result);
            return;
        }
        // Not ours: the executor calls a fixed server URL, but only the node that dispatched holds the
        // pending future — with sharding that is the task's owner, not necessarily the leader. Broadcast
        // so whichever node is actually waiting matches it by executionId; every other node ignores it
        // (see onPayload, which matches locally and never re-forwards, so there is no storm).
        cluster.multicastOn(CHANNEL, null, codec.encode(result), false);
    }

    @Override
    public void onPayload(Node sender, byte[] content) {
        CompleteRequest result = (CompleteRequest) codec.decode(content, CompleteRequest.class);
        CompletableFuture<CompleteRequest> future = pending.remove(result.executionId());
        if (future != null) {
            future.complete(result);
        } else {
            log.debug("Forwarded completion for unknown executionId {}", result.executionId());
        }
    }

    /** This scheduler node as {@code applicationName(instanceId@host:port)}, for the execution log. */
    private String schedulerRepr() {
        Node self = cluster.self();
        if (self == null) {
            return null;
        }
        return self.name() + "(" + self.id() + "@" + self.host() + ":" + self.port() + ")";
    }

    private String describeAwait(DispatchRequest request) {
        return (request.timeout() > 0 ? request.timeout() : maxAwaitMillis) + "ms";
    }

    private static String firstLine(String errorDetail) {
        if (errorDetail == null || errorDetail.isEmpty()) {
            return "";
        }
        int nl = errorDetail.indexOf('\n');
        return ": " + (nl > 0 ? errorDetail.substring(0, nl) : errorDetail);
    }

}
