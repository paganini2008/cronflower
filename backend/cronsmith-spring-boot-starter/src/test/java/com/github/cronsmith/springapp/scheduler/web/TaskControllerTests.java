package com.github.cronsmith.springapp.scheduler.web;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.github.cronsmith.springapp.scheduler.InMemoryTaskManager;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskSaveRequest;
import com.github.cronsmith.springapp.scheduler.TaskStatus;

/**
 * Drives {@link TaskController} against a real {@link InMemoryTaskManager} (so the CRUD, transitions,
 * run-now and log endpoints are all exercised end-to-end, along with {@link TaskDetailView}).
 */
class TaskControllerTests {

    private final InMemoryTaskManager store = new InMemoryTaskManager();
    private final TaskController controller = new TaskController(store, ZoneId.of("UTC"));

    private static TaskSaveRequest beanTask(String group, String name) {
        return new TaskSaveRequest(group, name, "bean", "com.example.Reports", "reports", "run",
                "p", null, null, null, null, "0 0 12 * * ?", "cron", "nightly", -1L, 2, 1000L,
                "FIRE_ONCE_NOW", -1, null);
    }

    @BeforeEach
    void seed() {
        controller.save(beanTask("reports", "nightly"), "app-a");
    }

    @Test
    void savesAndReadsBack() {
        ResponseEntity<TaskDetailView> got = controller.get("reports", "nightly");
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody().taskName()).isEqualTo("nightly");
    }

    @Test
    void savesAnHttpApiTask() {
        TaskSaveRequest http = new TaskSaveRequest("api", "ping", "HTTP", null, null, null, null,
                "http://example.com/ping", "GET", null, null, "0 0 * * * ?", "cron", "ping", -1L, 0,
                1000L, "SKIP", -1, null);
        TaskDetailView view = controller.save(http, null);
        assertThat(view.taskName()).isEqualTo("ping");
    }

    @Test
    void listsWithFiltersAndPaging() {
        Map<String, Object> all = controller.list(null, null, null, null, 0, 0);
        assertThat((List<?>) all.get("items")).isNotEmpty();
        Map<String, Object> filtered =
                controller.list("reports", "nightly", null, "STANDBY", 10, 0);
        assertThat((List<?>) filtered.get("items")).hasSize(1);
        // A filter matching nothing yields an empty page but still a total.
        assertThat((List<?>) controller.list("missing", null, null, null, 0, 5).get("items"))
                .isEmpty();
    }

    @Test
    void getAndDeleteMissingReturn404() {
        assertThat(controller.get("no", "such").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.delete("no", "such").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesTheTask() {
        assertThat(controller.delete("reports", "nightly").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.get("reports", "nightly").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void pauseResumeCancelDriveTheStateMachine() {
        assertThat(controller.pause("reports", "nightly").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(store.getTaskDetail(TaskId.of("reports", "nightly"), false).getTaskStatus())
                .isEqualTo(TaskStatus.PAUSED);
        assertThat(controller.resume("reports", "nightly").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.cancel("reports", "nightly").getStatusCode()).isEqualTo(HttpStatus.OK);
        // Transition on a missing task is a 404.
        assertThat(controller.pause("no", "such").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void logsAreEmptyForAFreshTask() {
        assertThat(controller.logs("reports", "nightly", 50, 0)).isEmpty();
    }

    @Test
    void runNowOnAMissingTaskIs404() {
        assertThat(controller.runNow("no", "such").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void runNowRecordsAFailureWhenThereIsNoExecutor() {
        // The seeded task is a remote-dispatch task; running it with no executor/dispatcher wired
        // fails, and run-now reports that failure rather than throwing.
        ResponseEntity<Map<String, Object>> response = controller.runNow("reports", "nightly");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(false);
        assertThat(controller.logs("reports", "nightly", 10, 0)).isNotEmpty();
    }
}
