package com.github.cronflow.springapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Drives the whole generate / validate / repair loop with a scripted fake provider, no network. */
class AiDagGeneratorTests {

    private final ObjectMapper mapper = new ObjectMapper();

    /** A provider that replays a scripted list of responses, one per chat call. */
    static final class FakeProvider implements LlmProvider {
        private final String id;
        private final boolean cloud;
        private final Deque<LlmResponse> scripted;
        int calls = 0;

        FakeProvider(String id, boolean cloud, LlmResponse... responses) {
            this.id = id;
            this.cloud = cloud;
            this.scripted = new ArrayDeque<>(List.of(responses));
        }

        @Override public String id() { return id; }
        @Override public String defaultModel() { return "fake-model"; }
        @Override public boolean cloud() { return cloud; }

        @Override public LlmResponse chat(LlmRequest request) {
            calls++;
            return scripted.isEmpty() ? LlmResponse.failed("no more scripted responses")
                    : scripted.poll();
        }
    }

    private NodeCatalog catalog() {
        return NodeCatalog.of(List.of(
                new CatalogNode("start", "entry", "startBean", "run", List.of(), List.of("x")),
                new CatalogNode("finish", "exit", "finishBean", "run", List.of("x"), List.of())));
    }

    private CronflowAiProperties props(int maxRepairs) {
        CronflowAiProperties p = new CronflowAiProperties();
        p.setEnabled(true);
        p.setDefaultProvider("ollama");
        p.getBudget().setMaxRepairs(maxRepairs);
        return p;
    }

    private static final String VALID_DAG = """
            {"graph":"flow","inputs":[],"channels":[],
             "nodes":[{"name":"start","entry":true,"trigger":"ALL","retries":0,"beanName":"startBean","methodName":"run","subgraph":null},
                      {"name":"finish","entry":false,"trigger":"ALL","retries":0,"beanName":"finishBean","methodName":"run","subgraph":null}],
             "edges":[{"from":"start","to":"finish","condition":null,"branch":null}],
             "conditionals":[],"shards":[]}""";

    private static final String GHOST_DAG = """
            {"graph":"flow","inputs":[],"channels":[],
             "nodes":[{"name":"ghost","entry":true,"trigger":"ALL","retries":0,"beanName":"x","methodName":"run","subgraph":null}],
             "edges":[],"conditionals":[],"shards":[]}""";

    private AiDagGenerator gen(CronflowAiProperties p, LlmProvider... providers) {
        return new AiDagGenerator(List.of(providers), p, new DagValidator(), mapper);
    }

    @Test
    void validOnFirstTry() {
        var provider = new FakeProvider("ollama", false, LlmResponse.of(VALID_DAG, 100, 50, "stop"));
        DagCandidate c = gen(props(2), provider).generate("make a flow", catalog(), null, false);
        assertThat(c.valid()).isTrue();
        assertThat(c.dag().graph()).isEqualTo("flow");
        assertThat(c.repairs()).isZero();
        assertThat(provider.calls).isEqualTo(1);
        assertThat(c.promptTokens()).isEqualTo(100);
    }

    @Test
    void invalidThenRepairedToValid() {
        var provider = new FakeProvider("ollama", false,
                LlmResponse.of(GHOST_DAG, 100, 40, "stop"),   // node not in catalog
                LlmResponse.of(VALID_DAG, 90, 50, "stop"));    // fixed on repair
        DagCandidate c = gen(props(2), provider).generate("make a flow", catalog(), null, false);
        assertThat(c.valid()).isTrue();
        assertThat(c.repairs()).isEqualTo(1);
        assertThat(provider.calls).isEqualTo(2);
        assertThat(c.promptTokens()).isEqualTo(190); // accumulated across calls
    }

    @Test
    void stillInvalidAfterRepairsSurfacesIssues() {
        var provider = new FakeProvider("ollama", false,
                LlmResponse.of(GHOST_DAG, 10, 10, "stop"),
                LlmResponse.of(GHOST_DAG, 10, 10, "stop"));
        DagCandidate c = gen(props(1), provider).generate("make a flow", catalog(), null, false);
        assertThat(c.valid()).isFalse();
        assertThat(c.issues()).anyMatch(i -> i.message().contains("not in the catalog"));
        assertThat(c.dag()).isNotNull(); // best candidate is still returned for manual fixing
    }

    @Test
    void malformedJsonIsRepairPrompted() {
        var provider = new FakeProvider("ollama", false,
                LlmResponse.of("not json at all", 10, 5, "stop"),
                LlmResponse.of(VALID_DAG, 10, 5, "stop"));
        DagCandidate c = gen(props(2), provider).generate("make a flow", catalog(), null, false);
        assertThat(c.valid()).isTrue();
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void jsonInsideMarkdownFenceIsParsed() {
        var provider = new FakeProvider("ollama", false,
                LlmResponse.of("```json\n" + VALID_DAG + "\n```", 10, 5, "stop"));
        DagCandidate c = gen(props(0), provider).generate("make a flow", catalog(), null, false);
        assertThat(c.valid()).isTrue();
    }

    @Test
    void providerErrorIsReported() {
        var provider = new FakeProvider("ollama", false, LlmResponse.failed("connection refused"));
        DagCandidate c = gen(props(2), provider).generate("make a flow", catalog(), null, false);
        assertThat(c.valid()).isFalse();
        assertThat(c.error()).contains("connection refused");
    }

    @Test
    void privateOnlyRefusesCloudProvider() {
        var cloud = new FakeProvider("openai", true, LlmResponse.of(VALID_DAG, 1, 1, "stop"));
        DagCandidate c = gen(props(2), cloud).generate("x", catalog(), "openai", true);
        assertThat(c.valid()).isFalse();
        assertThat(c.error()).contains("cloud provider");
        assertThat(cloud.calls).isZero(); // never called
    }

    @Test
    void unknownProviderFailsClosed() {
        var provider = new FakeProvider("ollama", false, LlmResponse.of(VALID_DAG, 1, 1, "stop"));
        DagCandidate c = gen(props(2), provider).generate("x", catalog(), "mystery", false);
        assertThat(c.valid()).isFalse();
        assertThat(c.error()).contains("no such provider");
    }
}
