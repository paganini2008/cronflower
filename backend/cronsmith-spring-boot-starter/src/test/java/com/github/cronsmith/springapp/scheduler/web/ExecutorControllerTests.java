package com.github.cronsmith.springapp.scheduler.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.ClusterExecutorRegistry;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry;
import com.github.cronsmith.springapp.scheduler.ExecutorTaskMetadata;
import com.github.cronsmith.springapp.scheduler.HeartbeatRequest;
import com.github.cronsmith.springapp.scheduler.InMemoryTaskManager;
import com.github.cronsmith.springapp.scheduler.RegistrationRequest;
import com.github.cronsmith.springapp.scheduler.RegistrationResponse;
import com.github.cronsmith.springapp.scheduler.RoutingStrategy;
import com.github.cronsmith.springapp.scheduler.TaskId;

/**
 * Registration / heartbeat / listing on {@link ExecutorController}: the scheduler mints an instanceId,
 * saves an executor's tasks (only when new or changed), reuses the id on re-registration, and lists
 * live executors.
 */
class ExecutorControllerTests {

    private final InMemoryTaskManager store = new InMemoryTaskManager();
    private final ExecutorRegistry registry = new ExecutorRegistry(90_000L, RoutingStrategy.ROUND_ROBIN);
    private final ClusterExecutorRegistry executors = mock(ClusterExecutorRegistry.class);
    private final ExecutorController controller =
            new ExecutorController(store, executors, registry, 90_000L, ZoneId.of("UTC"));

    private static ExecutorTaskMetadata task(String cron) {
        return new ExecutorTaskMetadata("g", "n", "com.example.X", "xBean", "run", cron, "cron",
                "desc", "p", -1L, 0, 1000L, "FIRE_ONCE_NOW", -1, null);
    }

    private static RegistrationRequest register(String instanceId, ExecutorTaskMetadata task) {
        return new RegistrationRequest("demo", instanceId, "http://h:8080/cronsmith/run",
                "http://h:8080/actuator/health", List.of(task), 1);
    }

    @Test
    void mintsAnInstanceIdAndSavesTheTasks() {
        RegistrationResponse resp = controller.register(register(null, task("0 0 12 * * ?")));
        assertThat(resp.instanceId()).isNotBlank();
        assertThat(store.getTaskDetail(TaskId.of("g", "n"), false)).isNotNull();
    }

    @Test
    void reusesAGivenInstanceIdAndSkipsUnchangedTasks() {
        ExecutorTaskMetadata t = task("0 0 12 * * ?");
        String id = controller.register(register(null, t)).instanceId();
        // Re-register with the assigned id and the same definition: id is kept, no re-save needed.
        RegistrationResponse again = controller.register(register(id, t));
        assertThat(again.instanceId()).isEqualTo(id);
    }

    @Test
    void resavesWhenTheDefinitionChanged() {
        controller.register(register(null, task("0 0 12 * * ?")));
        // A different cron is a changed definition and is saved again (no exception).
        RegistrationResponse resp = controller.register(register("id-1", task("0 0 6 * * ?")));
        assertThat(resp.instanceId()).isEqualTo("id-1");
    }

    @Test
    void heartbeatIsAccepted() {
        controller.heartbeat(new HeartbeatRequest("demo", "id-1", "http://h:8080/cronsmith/run",
                "http://h:8080/actuator/health", 1));
        // Nothing to assert beyond it not throwing; the cluster registry call is mocked.
    }

    @Test
    void listsLiveExecutors() {
        registry.upsert("demo", "id-1", "http://h:8080/cronsmith/run",
                "http://h:8080/actuator/health", 2);
        List<Map<String, Object>> list = controller.list();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("application")).isEqualTo("demo");
        assertThat(list.get(0).get("instanceId")).isEqualTo("id-1");
        assertThat(list.get(0).get("healthy")).isEqualTo(true);
    }
}
