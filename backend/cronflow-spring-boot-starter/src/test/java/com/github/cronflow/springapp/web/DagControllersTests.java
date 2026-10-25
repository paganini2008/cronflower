package com.github.cronflow.springapp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronflow.springapp.CronflowSchedulerProperties;
import com.github.cronflow.springapp.CronflowServerProperties;
import com.github.cronflow.springapp.dag.ClusterDagRegistry;
import com.github.cronflow.springapp.dag.DagCoordinator;
import com.github.cronflow.springapp.dag.DagExecutorRegistry;
import com.github.cronflow.springapp.dag.DagExecutorRegistry.ExecutorInstance;
import com.github.cronflow.springapp.dag.DagRunLog;
import com.github.cronflow.springapp.dag.EngineDagRunner;
import com.github.cronflow.springapp.pojo.DagCreateRequest;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagGraph;
import com.github.cronflow.springapp.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.pojo.DagNodeView;
import com.github.cronflow.springapp.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.pojo.DagRunDetail;
import com.github.cronflow.springapp.pojo.DagRunPage;
import com.github.cronflow.springapp.pojo.DagRunView;
import com.github.cronflow.springapp.pojo.DagGraphPage;
import com.github.cronflow.springapp.pojo.DagGraphView;
import com.github.cronflow.springapp.scheduler.TaskManager;
import com.github.cronflow.springapp.web.DagTriggerController.DagScheduleRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers the DAG REST controllers by direct method calls with mocked collaborators (no web server):
 * triggering, authoring, querying, registration and per-node health.
 */
class DagControllersTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DagDefinition def(String graph) {
        return new DagDefinition(graph, List.of(), List.of(),
                List.of(new DagDefinition.NodeDef("n", true, "ALL", 0, "b", "m", "")),
                List.of(), List.of(), List.of());
    }

    // ---- DagTriggerController ----

    private DagTriggerController triggerController(DagExecutorRegistry registry,
            DagCoordinator coordinator, TaskManager taskManager) {
        MockEnvironment env = new MockEnvironment().withProperty("server.port", "8080");
        return new DagTriggerController(registry, coordinator, taskManager, objectMapper, env,
                new CronflowSchedulerProperties(), new CronflowServerProperties());
    }

    @Test
    void triggerUnknownGraphIs404() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.definition("g")).thenReturn(null);
        ResponseEntity<Map<String, Object>> resp =
                triggerController(registry, mock(DagCoordinator.class), mock(TaskManager.class))
                        .trigger("g", Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void triggerNotRunnableIs422() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.definition("g")).thenReturn(def("g"));
        DagCoordinator coordinator = mock(DagCoordinator.class);
        when(coordinator.trigger(eq("g"), anyMap(), eq("manual"))).thenReturn(null);
        ResponseEntity<Map<String, Object>> resp =
                triggerController(registry, coordinator, mock(TaskManager.class)).trigger("g", null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void triggerSuccessReturnsRunId() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.definition("g")).thenReturn(def("g"));
        DagCoordinator coordinator = mock(DagCoordinator.class);
        when(coordinator.trigger(eq("g"), anyMap(), eq("manual"))).thenReturn("run-1");
        ResponseEntity<Map<String, Object>> resp =
                triggerController(registry, coordinator, mock(TaskManager.class))
                        .trigger("g", Map.of("k", "v"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("runId", "run-1").containsEntry("triggeredBy",
                "manual");
    }

    @Test
    void fireUsesScheduledTag() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.definition("g")).thenReturn(def("g"));
        DagCoordinator coordinator = mock(DagCoordinator.class);
        when(coordinator.trigger(eq("g"), anyMap(), eq("scheduled"))).thenReturn("run-2");
        ResponseEntity<Map<String, Object>> resp =
                triggerController(registry, coordinator, mock(TaskManager.class)).fire("g", Map.of());
        assertThat(resp.getBody()).containsEntry("triggeredBy", "scheduled");
    }

    @Test
    void scheduleRejectsUnknownGraphAndBlankCron() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.definition("missing")).thenReturn(null);
        when(registry.definition("g")).thenReturn(def("g"));
        DagTriggerController controller =
                triggerController(registry, mock(DagCoordinator.class), mock(TaskManager.class));
        assertThat(controller.schedule("missing",
                new DagScheduleRequest(null, null, "0 0 * * * ?", null, null, null, null, null, null,
                        null, null, null)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.schedule("g",
                new DagScheduleRequest(null, null, "  ", null, null, null, null, null, null,
                        null, null, null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void scheduleRegistersATask() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.definition("g")).thenReturn(def("g"));
        TaskManager taskManager = mock(TaskManager.class);
        DagTriggerController controller =
                triggerController(registry, mock(DagCoordinator.class), taskManager);
        ResponseEntity<Map<String, Object>> resp = controller.schedule("g",
                new DagScheduleRequest("grp", "job", "0 0 12 * * ?", "cron", "desc",
                        Map.of("seed", 1), 1000L, 2, 500L, -1, "FIRE_ONCE_NOW", null));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("taskGroup", "grp").containsEntry("taskName", "job");
    }

    @Test
    void scheduleUsesDefaultGroupAndName() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.definition("g")).thenReturn(def("g"));
        DagTriggerController controller =
                triggerController(registry, mock(DagCoordinator.class), mock(TaskManager.class));
        ResponseEntity<Map<String, Object>> resp = controller.schedule("g", new DagScheduleRequest(
                null, null, "0 0 12 * * ?", null, null, null, null, null, null, null, null, null));
        assertThat(resp.getBody()).containsEntry("taskGroup", "cronflow").containsEntry("taskName",
                "trigger-g");
    }

    // ---- DagAuthoringController ----

    @Test
    void authoringListsApplicationsAndValidates() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.liveApplications()).thenReturn(List.of("app"));
        ClusterDagRegistry cluster = mock(ClusterDagRegistry.class);
        DagAuthoringController controller = new DagAuthoringController(cluster, registry);
        assertThat(controller.applications()).containsExactly("app");

        // no graph name
        assertThat(controller.create(new DagCreateRequest("app",
                new DagDefinition(null, List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of()))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // no nodes
        assertThat(controller.create(new DagCreateRequest("app",
                new DagDefinition("g", List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of()))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // no application
        assertThat(controller.create(new DagCreateRequest("  ", def("g"))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // null request
        assertThat(controller.create(null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void authoringNoLiveExecutorIs422AndSuccessRegisters() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        ClusterDagRegistry cluster = mock(ClusterDagRegistry.class);
        DagAuthoringController controller = new DagAuthoringController(cluster, registry);
        when(registry.anyLiveInstance("app")).thenReturn(Optional.empty());
        assertThat(controller.create(new DagCreateRequest("app", def("g"))).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        when(registry.anyLiveInstance("app")).thenReturn(Optional.of(
                new ExecutorInstance("app", "i1", "run", "health", 1, java.time.Instant.now())));
        when(cluster.register(any())).thenReturn("i1");
        ResponseEntity<Map<String, Object>> ok =
                controller.create(new DagCreateRequest("app", def("g")));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsEntry("graph", "g");
    }

    // ---- DagQueryController ----

    @SuppressWarnings("unchecked")
    private ObjectProvider<EngineDagRunner> engineProvider() {
        return mock(ObjectProvider.class);
    }

    @Test
    void queryListsAndFiltersGraphs() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.graphs()).thenReturn(List.of(new DagGraph("app", def("alpha")),
                new DagGraph("app", def("beta"))));
        DagQueryController controller =
                new DagQueryController(registry, mock(DagRunLog.class), engineProvider());
        DagGraphPage page = controller.dags(null, 20, 0);
        assertThat(page.total()).isEqualTo(2);
        assertThat(controller.dags("alp", 20, 0).items()).extracting(DagGraphView::graph)
                .containsExactly("alpha");
        assertThat(controller.dags(null, 1, 0).items()).hasSize(1);
        assertThat(controller.dags(null, 0, 0).items()).hasSize(2); // limit<=0 -> all
    }

    @Test
    void queryOneGraphAnd404() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        when(registry.graphs()).thenReturn(List.of(new DagGraph("app", def("g"))));
        DagQueryController controller =
                new DagQueryController(registry, mock(DagRunLog.class), engineProvider());
        assertThat(controller.dag("app", "g").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.dag("app", "nope").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void queryRunsAndRunDetail() {
        DagRunLog runLog = mock(DagRunLog.class);
        DagRunView running = new DagRunView("r1", null, "app", "g", "t", "RUNNING", null, null, null,
                null, null, null, null, null);
        when(runLog.listRuns(null, null, null, 50, 0)).thenReturn(List.of(running));
        when(runLog.countRuns(null, null, null)).thenReturn(1);
        DagQueryController controller =
                new DagQueryController(mock(DagExecutorRegistry.class), runLog, engineProvider());
        DagRunPage page = controller.runs(null, null, null, 50, 0);
        assertThat(page.total()).isEqualTo(1);

        // unknown run -> 404
        when(runLog.findRun("none")).thenReturn(null);
        assertThat(controller.run("none").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // a RUNNING run reads the live frontier
        when(runLog.findRun("r1")).thenReturn(running);
        when(runLog.frontierOf("r1")).thenReturn(java.util.Set.of("n1"));
        when(runLog.nodesOf("r1")).thenReturn(List.<DagNodeView>of());
        when(runLog.childRuns("r1")).thenReturn(List.of());
        ResponseEntity<DagRunDetail> detail = controller.run("r1");
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().running()).containsExactly("n1");

        // a finished run does not read the frontier
        DagRunView done = new DagRunView("r2", null, "app", "g", "t", "SUCCESS", null, null, null,
                null, null, null, null, null);
        when(runLog.findRun("r2")).thenReturn(done);
        when(runLog.nodesOf("r2")).thenReturn(List.of());
        when(runLog.childRuns("r2")).thenReturn(List.of());
        assertThat(controller.run("r2").getBody().running()).isEmpty();
    }

    // ---- DagRegistrationController ----

    @Test
    void registrationDelegatesToClusterRegistry() {
        ClusterDagRegistry cluster = mock(ClusterDagRegistry.class);
        when(cluster.register(any())).thenReturn("i1");
        when(cluster.heartbeat(any())).thenReturn(true);
        DagRegistrationController controller = new DagRegistrationController(cluster);
        assertThat(controller.register(new DagRegistrationRequest("app", null, "run", "health",
                List.of(), List.of(), 1)).instanceId()).isEqualTo("i1");
        assertThat(controller
                .heartbeat(new DagHeartbeatRequest("app", "i1", "run", "health", 1)).known()).isTrue();
    }

    // ---- CronflowNodeHealthController ----

    private Node node(String id, String name, String host, String port) {
        return new Node(id, name, host, Integer.parseInt(port), 0L, 0L,
                com.chaconneai.spreader.NodeState.ALIVE, 0, Map.of("server.port", port));
    }

    @Test
    void nodeHealthMetaAndSelfHealth() {
        GossipCluster cluster = mock(GossipCluster.class);
        Node self = node("id-1", "app", "127.0.0.1", "8080");
        when(cluster.self()).thenReturn(self);
        when(cluster.leader()).thenReturn(self);
        CronflowNodeHealthController controller = new CronflowNodeHealthController(cluster,
                new MockEnvironment().withProperty("spring.profiles.active", "dev"),
                new CronflowServerProperties());
        Map<String, Object> meta = controller.meta();
        assertThat(meta).containsEntry("application", "app");
        Map<String, Object> self2 = controller.selfHealth();
        assertThat(self2).containsEntry("status", "UP").containsEntry("leader", true);
        assertThat(self2.get("memory")).isInstanceOf(Map.class);
    }

    @Test
    void nodeHealthMetaDefaultsEnvWhenNoProfile() {
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.self()).thenReturn(node("id-1", "app", "127.0.0.1", "8080"));
        CronflowNodeHealthController controller = new CronflowNodeHealthController(cluster,
                new MockEnvironment(), new CronflowServerProperties());
        assertThat(controller.meta()).containsEntry("env", "dev");
    }

    @Test
    void nodesHealthMarksUnreachableMembersDown() {
        GossipCluster cluster = mock(GossipCluster.class);
        Node member = node("id-1", "app", "127.0.0.1", "59999"); // nothing listening -> DOWN
        when(cluster.members()).thenReturn(List.of(member));
        when(cluster.leader()).thenReturn(member);
        CronflowNodeHealthController controller = new CronflowNodeHealthController(cluster,
                new MockEnvironment(), new CronflowServerProperties());
        List<Map<String, Object>> rows = controller.nodesHealth();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("status", "DOWN").containsEntry("leader", true);
    }
}
