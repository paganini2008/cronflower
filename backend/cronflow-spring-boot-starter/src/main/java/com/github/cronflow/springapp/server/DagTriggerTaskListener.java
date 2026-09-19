package com.github.cronflow.springapp.server;

import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskListener;

/**
 * The zero-intrusion seam that turns a finished cronsmith task into a DAG run. Implements cronsmith's
 * public {@link TaskListener} and is attached to the running scheduler by {@link TaskListenerAttacher}
 * — cronsmith itself is never modified and {@code @Task} gains no attribute.
 *
 * <p>
 * On {@link #onTaskEnded}, if the task is bound to a graph (a cronsmith {@code @Task} that sits in a
 * {@code @Dag} class) and it succeeded, its return value becomes the DAG's initial state and the
 * workflow runs. The task's only job was to produce that initial value.
 *
 * @Description: DagTriggerTaskListener
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class DagTriggerTaskListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(DagTriggerTaskListener.class);

    /** Conventional channel a scalar task return value lands in. */
    public static final String INPUT_CHANNEL = "input";

    private final DagExecutorRegistry registry;
    private final DagCoordinator coordinator;
    private final ObjectMapper objectMapper;

    public DagTriggerTaskListener(DagExecutorRegistry registry, DagCoordinator coordinator,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.coordinator = coordinator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onTaskEnded(LocalDateTime firedDateTime, TaskDetail taskDetail, Object returnValue,
            Throwable e) {
        if (e != null) {
            return; // a failed task does not seed a DAG
        }
        TaskId id = taskDetail.getTask().getTaskId();
        registry.triggeredGraph(id.getGroup(), id.getName()).ifPresent(graph -> {
            try {
                coordinator.trigger(graph, toInitialState(returnValue),
                        id.getGroup() + "/" + id.getName());
            } catch (RuntimeException ex) {
                log.warn("cronflow: failed to trigger graph '{}' from task {}/{}: {}", graph,
                        id.getGroup(), id.getName(), ex.toString());
            }
        });
    }

    /**
     * Turns the task's return value into the initial channels. A {@code Map} is used as-is (its entries
     * become channels); a JSON-object string (how a task's {@code Map} return arrives back over HTTP) is
     * parsed and spread the same way; anything else — a scalar, a JSON array, a plain string — lands
     * whole under {@link #INPUT_CHANNEL}.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toInitialState(Object returnValue) {
        if (returnValue == null) {
            return Map.of();
        }
        if (returnValue instanceof Map) {
            return (Map<String, Object>) returnValue;
        }
        if (returnValue instanceof String s) {
            String t = s.trim();
            if (t.startsWith("{") && t.endsWith("}")) {
                try {
                    return objectMapper.readValue(t, Map.class);
                } catch (RuntimeException ignored) {
                    // not a JSON object after all; fall through and treat it as a plain scalar
                }
            }
        }
        return Map.of(INPUT_CHANNEL, returnValue);
    }

}
