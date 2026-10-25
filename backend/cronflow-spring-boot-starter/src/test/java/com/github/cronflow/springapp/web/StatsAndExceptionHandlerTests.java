package com.github.cronflow.springapp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.github.cronflow.springapp.scheduler.ExecutorRegistry;
import com.github.cronflow.springapp.scheduler.ExecutorRegistry.ExecutorInstance;
import com.github.cronflow.springapp.scheduler.TaskDetailNotFoundException;
import com.github.cronflow.springapp.scheduler.TaskException;
import com.github.cronflow.springapp.scheduler.TaskManager;
import com.github.cronflow.springapp.scheduler.TaskQuery;

/**
 * Covers the dashboard stats aggregation and the server exception handler's status mapping.
 */
class StatsAndExceptionHandlerTests {

    @Test
    void statsAggregatesTaskAndExecutorCounts() {
        TaskManager taskManager = mock(TaskManager.class);
        when(taskManager.getTaskCount(any(TaskQuery.class))).thenReturn(4);
        ExecutorRegistry registry = mock(ExecutorRegistry.class);
        long now = System.currentTimeMillis();
        when(registry.snapshot()).thenReturn(List.of(
                new ExecutorInstance("app", "live", "run", "health", now, 1),
                new ExecutorInstance("app", "stale", "run", "health", now - 100_000L, 1)));
        StatsController controller = new StatsController(taskManager, registry, 30_000L);
        var stats = controller.stats();
        assertThat(stats).containsKey("tasksByStatus");
        assertThat(stats.get("executorsTotal")).isEqualTo(2);
        assertThat(stats.get("executorsLive")).isEqualTo(1L);
        assertThat(stats.get("taskTotal")).isEqualTo(4);
    }

    @Test
    void exceptionHandlerMapsStatuses() {
        ServerExceptionHandler handler = new ServerExceptionHandler();
        ResponseEntity<ServerExceptionHandler.ErrorResponse> notFound =
                handler.onNotFound(new TaskDetailNotFoundException("missing"));
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody().status()).isEqualTo(404);

        ResponseEntity<ServerExceptionHandler.ErrorResponse> bad =
                handler.onBadRequest(new IllegalArgumentException("nope"));
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody().message()).isEqualTo("nope");

        ResponseEntity<ServerExceptionHandler.ErrorResponse> taskErr =
                handler.onTaskException(new TaskException("boom"));
        assertThat(taskErr.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ResponseEntity<ServerExceptionHandler.ErrorResponse> other =
                handler.onOther(new RuntimeException("unexpected"));
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(other.getBody().error()).isEqualTo("Internal Server Error");
    }
}
