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
package com.github.cronflow.springapp.server.jpa;

import com.github.cronflow.springapp.server.DagIds;
import com.github.cronflow.springapp.server.DagRunLog;
import com.github.cronflow.springapp.server.pojo.DagRunView;
import com.github.cronflow.springapp.server.pojo.DagNodeView;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import com.github.cronflow.springapp.server.jpa.DagLogEntity;
import com.github.cronflow.springapp.server.jpa.DagNodeLogEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * JPA-backed {@link DagRunLog} (cf_dag_log + cf_dag_node_log).
 *
 * @Description: JpaDagRunLog
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class JpaDagRunLog implements DagRunLog {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void begin(String runId, String parentRunId, String application, String graph,
            String triggeredBy, LocalDateTime startedAt, String inputParameter) {
        DagLogEntity e = new DagLogEntity();
        e.setRunId(runId);
        e.setParentRunId(parentRunId);
        e.setApplication(application);
        e.setGraph(graph);
        e.setTriggeredBy(triggeredBy);
        e.setStatus("RUNNING");
        e.setStartedAt(startedAt);
        e.setInputParameter(inputParameter);
        entityManager.merge(e);
    }

    @Override
    @Transactional
    public void finish(String runId, String status, LocalDateTime finishedAt, long elapsedMs,
            int nodeCount, String failedNode, String errorDetail, String returnValue) {
        DagLogEntity e = entityManager.find(DagLogEntity.class, runId);
        if (e == null) {
            e = new DagLogEntity();
            e.setRunId(runId);
        }
        e.setStatus(status);
        e.setFinishedAt(finishedAt);
        e.setElapsedMs(elapsedMs);
        e.setNodeCount(nodeCount);
        e.setFailedNode(failedNode);
        e.setErrorDetail(errorDetail);
        e.setReturnValue(returnValue);
        entityManager.merge(e);
    }

    @Override
    @Transactional
    public void node(String runId, String graph, String node, int seq, String status,
            String inputParam, String output, String executor, long elapsedMs, String errorDetail) {
        DagNodeLogEntity e = new DagNodeLogEntity();
        e.setId(DagIds.nodeLogId(runId, seq));
        e.setRunId(runId);
        e.setGraph(graph);
        e.setNode(node);
        e.setSeq(seq);
        e.setStatus(status);
        e.setInputParam(inputParam);
        e.setOutput(output);
        e.setExecutor(executor);
        e.setElapsedMs(elapsedMs);
        e.setErrorDetail(errorDetail);
        e.setLoggedAt(LocalDateTime.now());
        entityManager.merge(e);
    }

    // ---- read side -----------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<DagRunView> listRuns(String application, String graph, String status, int limit,
            int offset) {
        TypedQuery<DagLogEntity> q = entityManager.createQuery(
                "select d from DagLogEntity d" + whereClause(application, graph, status)
                        + " order by d.startedAt desc",
                DagLogEntity.class);
        bindFilters(q, application, graph, status);
        return q.setFirstResult(Math.max(0, offset)).setMaxResults(Math.max(1, limit))
                .getResultList().stream().map(JpaDagRunLog::toRunView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int countRuns(String application, String graph, String status) {
        TypedQuery<Long> q = entityManager.createQuery(
                "select count(d) from DagLogEntity d" + whereClause(application, graph, status),
                Long.class);
        bindFilters(q, application, graph, status);
        return q.getSingleResult().intValue();
    }

    @Override
    @Transactional(readOnly = true)
    public DagRunView findRun(String runId) {
        DagLogEntity e = entityManager.find(DagLogEntity.class, runId);
        return e == null ? null : toRunView(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DagNodeView> nodesOf(String runId) {
        return entityManager.createQuery(
                "select n from DagNodeLogEntity n where n.runId = :runId order by n.seq asc",
                DagNodeLogEntity.class).setParameter("runId", runId)
                .getResultList().stream().map(JpaDagRunLog::toNodeView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DagRunView> childRuns(String parentRunId) {
        return entityManager.createQuery(
                "select d from DagLogEntity d where d.parentRunId = :parentRunId"
                        + " order by d.startedAt desc",
                DagLogEntity.class).setParameter("parentRunId", parentRunId)
                .getResultList().stream().map(JpaDagRunLog::toRunView).toList();
    }

    private static String whereClause(String application, String graph, String status) {
        StringBuilder sb = new StringBuilder();
        if (isSet(application)) {
            sb.append(sb.isEmpty() ? " where" : " and").append(" d.application = :application");
        }
        if (isSet(graph)) {
            sb.append(sb.isEmpty() ? " where" : " and").append(" d.graph = :graph");
        }
        if (isSet(status)) {
            sb.append(sb.isEmpty() ? " where" : " and").append(" d.status = :status");
        }
        return sb.toString();
    }

    private static void bindFilters(TypedQuery<?> q, String application, String graph, String status) {
        if (isSet(application)) {
            q.setParameter("application", application);
        }
        if (isSet(graph)) {
            q.setParameter("graph", graph);
        }
        if (isSet(status)) {
            q.setParameter("status", status);
        }
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static DagRunView toRunView(DagLogEntity e) {
        return new DagRunView(e.getRunId(), e.getParentRunId(), e.getApplication(), e.getGraph(),
                e.getTriggeredBy(), e.getStatus(), e.getStartedAt(), e.getFinishedAt(),
                e.getElapsedMs(), e.getNodeCount(), e.getFailedNode(), e.getInputParameter(),
                e.getReturnValue(), e.getErrorDetail());
    }

    private static DagNodeView toNodeView(DagNodeLogEntity e) {
        return new DagNodeView(e.getRunId(), e.getGraph(), e.getNode(), e.getSeq(), e.getStatus(),
                e.getInputParam(), e.getOutput(), e.getExecutor(), e.getElapsedMs(),
                e.getErrorDetail(), e.getLoggedAt());
    }

}
