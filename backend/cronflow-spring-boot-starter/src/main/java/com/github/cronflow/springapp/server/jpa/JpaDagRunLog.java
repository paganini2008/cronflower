package com.github.cronflow.springapp.server.jpa;

import com.github.cronflow.springapp.server.DagRunLog;
import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;
import com.github.cronflow.springapp.server.jpa.DagLogEntity;
import com.github.cronflow.springapp.server.jpa.DagNodeLogEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
        e.setId(DagNodeLogEntity.idOf(runId, seq));
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

}
