package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.github.cronflow.springapp.scheduler.Task;
import com.github.cronflow.springapp.scheduler.TaskDetail;
import com.github.cronflow.springapp.scheduler.TaskId;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers the zero-intrusion seam that fires a DAG when a bound task ends: the early return on a failed
 * task, the no-binding case, and each branch of turning a task return value into the initial state.
 */
class DagTriggerTaskListenerTests {

    private final DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
    private final DagCoordinator coordinator = mock(DagCoordinator.class);
    private final DagTriggerTaskListener listener =
            new DagTriggerTaskListener(registry, coordinator, new ObjectMapper());

    private TaskDetail taskDetail(String group, String name) {
        TaskDetail td = mock(TaskDetail.class);
        Task task = mock(Task.class);
        when(td.getTask()).thenReturn(task);
        when(task.getTaskId()).thenReturn(TaskId.of(group, name));
        return td;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedState() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(coordinator).trigger(eq("g"), captor.capture(), eq("grp/name"));
        return captor.getValue();
    }

    @Test
    void failedTaskDoesNotTrigger() {
        listener.onTaskEnded(LocalDateTime.now(), taskDetail("grp", "name"), "x",
                new RuntimeException("failed"));
        verify(coordinator, never()).trigger(any(), any(), any());
    }

    @Test
    void unboundTaskDoesNotTrigger() {
        when(registry.triggeredGraph("grp", "name")).thenReturn(Optional.empty());
        listener.onTaskEnded(LocalDateTime.now(), taskDetail("grp", "name"), "x", null);
        verify(coordinator, never()).trigger(any(), any(), any());
    }

    @Test
    void mapReturnValueBecomesChannelsDirectly() {
        when(registry.triggeredGraph("grp", "name")).thenReturn(Optional.of("g"));
        listener.onTaskEnded(LocalDateTime.now(), taskDetail("grp", "name"), Map.of("a", 1), null);
        assertThat(capturedState()).containsEntry("a", 1);
    }

    @Test
    void jsonObjectStringIsParsed() {
        when(registry.triggeredGraph("grp", "name")).thenReturn(Optional.of("g"));
        listener.onTaskEnded(LocalDateTime.now(), taskDetail("grp", "name"), "{\"k\":\"v\"}", null);
        assertThat(capturedState()).containsEntry("k", "v");
    }

    @Test
    void invalidJsonObjectStringFallsBackToInputChannel() {
        when(registry.triggeredGraph("grp", "name")).thenReturn(Optional.of("g"));
        listener.onTaskEnded(LocalDateTime.now(), taskDetail("grp", "name"), "{not json}", null);
        assertThat(capturedState()).containsEntry(DagTriggerTaskListener.INPUT_CHANNEL, "{not json}");
    }

    @Test
    void scalarReturnValueGoesUnderInputChannel() {
        when(registry.triggeredGraph("grp", "name")).thenReturn(Optional.of("g"));
        listener.onTaskEnded(LocalDateTime.now(), taskDetail("grp", "name"), 42, null);
        assertThat(capturedState()).containsEntry(DagTriggerTaskListener.INPUT_CHANNEL, 42);
    }

    @Test
    void nullReturnValueYieldsEmptyState() {
        when(registry.triggeredGraph("grp", "name")).thenReturn(Optional.of("g"));
        listener.onTaskEnded(LocalDateTime.now(), taskDetail("grp", "name"), null, null);
        assertThat(capturedState()).isEmpty();
    }

    @Test
    void coordinatorFailureIsSwallowed() {
        when(registry.triggeredGraph("grp", "name")).thenReturn(Optional.of("g"));
        when(coordinator.trigger(any(), any(), any())).thenThrow(new RuntimeException("engine down"));
        // must not propagate
        listener.onTaskEnded(LocalDateTime.now(), taskDetail("grp", "name"), "x", null);
        verify(coordinator).trigger(eq("g"), any(), eq("grp/name"));
    }
}
