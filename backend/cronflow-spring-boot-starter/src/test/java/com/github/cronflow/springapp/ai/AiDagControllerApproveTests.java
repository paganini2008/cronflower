package com.github.cronflow.springapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.github.cronflow.springapp.ai.AiDagController.ApproveRequest;
import com.github.cronflow.springapp.ai.AiDagController.ApproveResponse;
import com.github.cronflow.springapp.ai.AiDagController.GenerateRequest;
import com.github.cronflow.springapp.ai.AiDagController.GenerateResponse;
import com.github.cronflow.springapp.dag.ClusterDagRegistry;
import com.github.cronflow.springapp.dag.DagDefinitionCodec;
import com.github.cronflow.springapp.dag.DagExecutorRegistry;
import com.github.cronflow.springapp.dag.DagExecutorRegistry.ExecutorInstance;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagDefinition.EdgeDef;
import com.github.cronflow.springapp.pojo.DagDefinition.NodeDef;
import tools.jackson.databind.ObjectMapper;

/** Covers the approve gate (validation, no-executor, success + registration) and that generate records. */
class AiDagControllerApproveTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClusterDagRegistry clusterRegistry = mock(ClusterDagRegistry.class);
    private final DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
    private final InMemoryAiGenerationStore store = new InMemoryAiGenerationStore();

    private NodeCatalog catalog() {
        return NodeCatalog.of(List.of(
                new CatalogNode("start", "entry", "startBean", "run", List.of(), List.of()),
                new CatalogNode("finish", "exit", "finishBean", "run", List.of(), List.of())));
    }

    private LlmProvider dummy() {
        return new AiDagGeneratorTests.FakeProvider("ollama", false);
    }

    private AiDagController controller(LlmProvider provider) {
        var props = new CronflowAiProperties();
        var validator = new DagValidator();
        var generator = new AiDagGenerator(List.of(provider), props, validator, mapper);
        NodeCatalogProvider cat = this::catalog;
        return new AiDagController(generator, cat, validator, new DagDefinitionCodec(mapper),
                clusterRegistry, registry, store);
    }

    private DagDefinition validDag() {
        return new DagDefinition("flow", List.of(), List.of(),
                List.of(new NodeDef("start", true, "ALL", 0, "startBean", "run", null),
                        new NodeDef("finish", false, "ALL", 0, "finishBean", "run", null)),
                List.of(new EdgeDef("start", "finish", null, null)), List.of(), List.of());
    }

    private DagDefinition ghostDag() {
        return new DagDefinition("flow", List.of(), List.of(),
                List.of(new NodeDef("ghost", true, "ALL", 0, "x", "run", null)), List.of(), List.of(),
                List.of());
    }

    @Test
    void approveRejectsInvalidDagAndDoesNotRegister() {
        var resp = controller(dummy()).approve(new ApproveRequest("id1", ghostDag(), "billing", "bob"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        ApproveResponse body = resp.getBody();
        assertThat(body.registered()).isFalse();
        assertThat(body.issues()).anyMatch(i -> i.message().contains("not in the catalog"));
        verify(clusterRegistry, never()).register(any());
    }

    @Test
    void approveReturns422WhenNoLiveExecutor() {
        when(registry.anyLiveInstance("billing")).thenReturn(Optional.empty());
        var resp = controller(dummy()).approve(new ApproveRequest("id1", validDag(), "billing", "bob"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().error()).contains("no live executor");
        verify(clusterRegistry, never()).register(any());
    }

    @Test
    void approveRegistersValidDagAndMarksRecordApproved() {
        store.save(new AiGenerationRecord("id1", "p", "ollama", "m", AiGenerationRecord.DRAFT, "flow",
                null, "{}", null, 0, 0, 0, null, null, LocalDateTime.now(), null, null));
        when(registry.anyLiveInstance("billing")).thenReturn(Optional.of(
                new ExecutorInstance("billing", "i1", "http://run", "http://health", 1, Instant.now())));

        var resp = controller(dummy()).approve(new ApproveRequest("id1", validDag(), "billing", "bob"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().registered()).isTrue();
        assertThat(resp.getBody().graph()).isEqualTo("flow");
        verify(clusterRegistry).register(any());
        assertThat(store.find("id1").orElseThrow().status()).isEqualTo(AiGenerationRecord.APPROVED);
        assertThat(store.find("id1").orElseThrow().approvedBy()).isEqualTo("bob");
    }

    @Test
    void generatePersistsADraftRecord() {
        String validJson = """
                {"graph":"flow","inputs":[],"channels":[],
                 "nodes":[{"name":"start","entry":true,"trigger":"ALL","retries":0,"beanName":"startBean","methodName":"run","subgraph":null}],
                 "edges":[],"conditionals":[],"shards":[]}""";
        LlmProvider provider = new AiDagGeneratorTests.FakeProvider("ollama", false,
                LlmResponse.of(validJson, 42, 7, "stop"));
        var resp = controller(provider).generate(new GenerateRequest("make a flow", null, false));
        GenerateResponse body = resp.getBody();
        assertThat(body.valid()).isTrue();
        assertThat(body.id()).isNotBlank();
        var saved = store.find(body.id()).orElseThrow();
        assertThat(saved.status()).isEqualTo(AiGenerationRecord.DRAFT);
        assertThat(saved.promptTokens()).isEqualTo(42);
        assertThat(saved.candidate()).contains("flow");
    }
}
