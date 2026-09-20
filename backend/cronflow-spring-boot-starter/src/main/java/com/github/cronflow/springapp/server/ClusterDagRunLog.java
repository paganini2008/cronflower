/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cronflow.springapp.server;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import com.chaconneai.openspreader.serialization.ObjectCodec;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.GossipListener;
import com.github.cronflow.springapp.server.pojo.DagNodeView;
import com.github.cronflow.springapp.server.pojo.DagRunLogMessage;
import com.github.cronflow.springapp.server.pojo.DagRunView;

/**
 * Replicates {@link DagRunLog} writes across the cluster, so the run history ({@code cf_dag_log} +
 * {@code cf_dag_node_log}) lives on <b>every</b> node in the node-local store model (per-node
 * H2/SQLite) — exactly as {@link ClusterDagRegistry} replicates definitions and as cronsmith
 * replicates its task log. Without it the console, load-balanced across nodes, would see history only
 * from whichever node happened to run the DAG (the leader).
 *
 * <p>
 * Replication is skipped for a <b>shared</b> store (MySQL/PostgreSQL/…): every node already reads the
 * same rows there, so broadcasting would just re-insert them and collide on the primary key. Reads
 * always go to the local store, which the replication (or the shared DB) has made complete.
 *
 * @Description: ClusterDagRunLog
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class ClusterDagRunLog
        implements DagRunLog, GossipListener, ApplicationListener<ApplicationReadyEvent> {

    public static final String CHANNEL = "cronflow.dag-runs";

    private static final Logger log = LoggerFactory.getLogger(ClusterDagRunLog.class);

    private final DagRunLog store;
    private final GossipCluster cluster;
    private final ObjectCodec codec;
    private final boolean replicate;

    /** Live in-flight frontier per run (transient, not persisted). Replicated so any node can answer the
     *  run-detail query behind the round-robin console proxy; the console pulses these nodes. */
    private final Map<String, Set<String>> frontier = new ConcurrentHashMap<>();

    public ClusterDagRunLog(DagRunLog store, GossipCluster cluster, ObjectCodec codec,
            boolean replicate) {
        this.store = store;
        this.cluster = cluster;
        this.codec = codec;
        this.replicate = replicate;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (replicate) {
            cluster.addListener(CHANNEL, this);
            log.info("cronflow: cluster DAG run-log replicating on '{}'", CHANNEL);
        }
    }

    @Override
    public void begin(String runId, String parentRunId, String application, String graph,
            String triggeredBy, LocalDateTime startedAt, String inputParameter) {
        store.begin(runId, parentRunId, application, graph, triggeredBy, startedAt, inputParameter);
        propagate(DagRunLogMessage.begin(runId, parentRunId, application, graph, triggeredBy,
                startedAt, inputParameter));
    }

    @Override
    public void finish(String runId, String status, LocalDateTime finishedAt, long elapsedMs,
            int nodeCount, String failedNode, String errorDetail, String returnValue) {
        store.finish(runId, status, finishedAt, elapsedMs, nodeCount, failedNode, errorDetail,
                returnValue);
        propagate(DagRunLogMessage.finish(runId, status, finishedAt, elapsedMs, nodeCount, failedNode,
                errorDetail, returnValue));
    }

    @Override
    public void node(String runId, String graph, String node, int seq, String status,
            String inputParam, String output, String executor, long elapsedMs, String errorDetail) {
        store.node(runId, graph, node, seq, status, inputParam, output, executor, elapsedMs,
                errorDetail);
        propagate(DagRunLogMessage.node(runId, graph, node, seq, status, inputParam, output, executor,
                elapsedMs, errorDetail));
    }

    @Override
    public void frontier(String runId, Set<String> running) {
        Set<String> snap = running == null ? Set.of() : Set.copyOf(running);
        frontier.put(runId, snap);
        propagate(DagRunLogMessage.frontier(runId, snap));
    }

    @Override
    public Set<String> frontierOf(String runId) {
        return frontier.getOrDefault(runId, Set.of());
    }

    @Override
    public void clearFrontier(String runId) {
        frontier.remove(runId);
        propagate(DagRunLogMessage.frontier(runId, Set.of()));
    }

    private void propagate(DagRunLogMessage message) {
        if (!replicate) {
            return;
        }
        try {
            byte[] payload = codec.encode(message);
            if (cluster.isLeader()) {
                cluster.multicastOn(CHANNEL, null, payload, false);
            } else {
                cluster.sendToLeaderOn(CHANNEL, payload);
            }
        } catch (RuntimeException e) {
            log.warn("cronflow: failed to replicate run-log {}: {}", message.op(), e.toString());
        }
    }

    @Override
    public void onPayload(Node sender, byte[] content) {
        DagRunLogMessage message = (DagRunLogMessage) codec.decode(content, DagRunLogMessage.class);
        applyRemote(message);
        // A follower forwarded this to us; as leader, fan it out to the rest.
        if (cluster.isLeader()) {
            cluster.multicastOn(CHANNEL, null, content, false);
        }
    }

    /** Apply a replicated write to the local store only (never re-propagate — that is the sender's job). */
    private void applyRemote(DagRunLogMessage m) {
        try {
            switch (m.op()) {
                case DagRunLogMessage.BEGIN -> store.begin(m.runId(), m.parentRunId(), m.application(),
                        m.graph(), m.triggeredBy(), m.startedAt(), m.inputParameter());
                case DagRunLogMessage.FINISH -> store.finish(m.runId(), m.status(), m.finishedAt(),
                        m.elapsedMs() == null ? 0L : m.elapsedMs(),
                        m.nodeCount() == null ? 0 : m.nodeCount(), m.failedNode(), m.errorDetail(),
                        m.returnValue());
                case DagRunLogMessage.NODE -> store.node(m.runId(), m.graph(), m.node(),
                        m.seq() == null ? 0 : m.seq(), m.status(), m.inputParam(), m.output(),
                        m.executor(), m.elapsedMs() == null ? 0L : m.elapsedMs(), m.errorDetail());
                case DagRunLogMessage.FRONTIER -> {
                    // Transient (never touches the store): just mirror the in-flight set locally.
                    if (m.frontier() == null || m.frontier().isEmpty()) {
                        frontier.remove(m.runId());
                    } else {
                        frontier.put(m.runId(), Set.copyOf(m.frontier()));
                    }
                }
                default -> log.warn("cronflow: unknown run-log op '{}'", m.op());
            }
        } catch (RuntimeException e) {
            log.debug("cronflow: applying replicated run-log {} failed: {}", m.op(), e.toString());
        }
    }

    // ---- reads: the local store is complete (replicated, or a shared DB) --------------------------

    @Override
    public List<DagRunView> listRuns(String application, String graph, String status, int limit,
            int offset) {
        return store.listRuns(application, graph, status, limit, offset);
    }

    @Override
    public int countRuns(String application, String graph, String status) {
        return store.countRuns(application, graph, status);
    }

    @Override
    public DagRunView findRun(String runId) {
        return store.findRun(runId);
    }

    @Override
    public List<DagNodeView> nodesOf(String runId) {
        return store.nodesOf(runId);
    }

    @Override
    public List<DagRunView> childRuns(String parentRunId) {
        return store.childRuns(parentRunId);
    }

}
