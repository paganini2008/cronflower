package com.github.cronflow.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import com.github.cronflow.springapp.executor.pojo.NodeRunRequest;
import com.github.cronflow.springapp.executor.pojo.NodeRunResult;

/**
 * Covers the default node-execution service (HTTP → bean + method resolution and invocation) and the
 * thin controller that fronts it.
 */
class NodeExecutionTests {

    private GenericApplicationContext ctx;
    private DefaultNodeExecutionService service;

    @BeforeEach
    void setUp() {
        ctx = new GenericApplicationContext();
        ctx.registerBean("nodeBean", NodeBean.class);
        ctx.refresh();
        service = new DefaultNodeExecutionService(ctx);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private NodeRunRequest req(String method, Map<String, Object> state) {
        return new NodeRunRequest("g", "n", "nodeBean", method, state);
    }

    @Test
    void invokesMethodTakingDagStateAndReturnsUpdates() {
        NodeRunResult result = service.run(req("withState", Map.of("x", 5)));
        assertThat(result.ok()).isTrue();
        assertThat(result.updates()).containsEntry("doubled", 10);
    }

    @Test
    void invokesNoArgMethod() {
        NodeRunResult result = service.run(req("noArg", null));
        assertThat(result.ok()).isTrue();
        assertThat(result.updates()).containsEntry("ran", true);
    }

    @Test
    void voidReturnYieldsEmptyUpdates() {
        NodeRunResult result = service.run(req("voidNode", Map.of()));
        assertThat(result.ok()).isTrue();
        assertThat(result.updates()).isEmpty();
    }

    @Test
    void unknownMethodFails() {
        NodeRunResult result = service.run(req("missing", Map.of()));
        assertThat(result.ok()).isFalse();
        assertThat(result.errorDetail()).contains("No node method 'missing'");
    }

    @Test
    void throwingMethodFailsWithCause() {
        NodeRunResult result = service.run(req("boom", Map.of()));
        assertThat(result.ok()).isFalse();
        assertThat(result.errorDetail()).contains("boom");
    }

    @Test
    void unknownBeanFails() {
        NodeRunResult result =
                service.run(new NodeRunRequest("g", "n", "ghostBean", "withState", Map.of()));
        assertThat(result.ok()).isFalse();
    }

    @Test
    void controllerDelegatesToService() {
        NodeRunController controller = new NodeRunController(service);
        NodeRunResult result = controller.run(req("noArg", null));
        assertThat(result.ok()).isTrue();
        assertThat(NodeRunController.RUN_NODE_PATH).isEqualTo("/cronflow/nodes/run");
    }

    static class NodeBean {
        public Map<String, Object> withState(DagState state) {
            return Map.of("doubled", state.getInt("x") * 2);
        }

        public Map<String, Object> noArg() {
            return Map.of("ran", true);
        }

        public void voidNode() {}

        public Map<String, Object> boom() {
            throw new IllegalStateException("boom");
        }
    }
}
