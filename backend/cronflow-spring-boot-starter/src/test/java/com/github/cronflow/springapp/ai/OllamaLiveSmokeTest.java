package com.github.cronflow.springapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import com.github.cronflow.springapp.ai.CronflowAiProperties.Provider;
import tools.jackson.databind.ObjectMapper;

/**
 * Live smoke against a private Ollama endpoint. Off by default; enable with CRONFLOW_AI_LIVE=true and
 * supply CRONFLOW_AI_OLLAMA_URL, CRONFLOW_AI_OLLAMA_KEY and (optionally) CRONFLOW_AI_MODEL. It drives the
 * real provider and generator, proving the HTTP path and JSON contract end to end. No secret is stored
 * here; all of it comes from the environment.
 */
@EnabledIfEnvironmentVariable(named = "CRONFLOW_AI_LIVE", matches = "true")
class OllamaLiveSmokeTest {

    @Test
    void generatesADagFromAPrompt() {
        Provider cfg = new Provider();
        cfg.setKind("compatible");
        cfg.setBaseUrl(System.getenv("CRONFLOW_AI_OLLAMA_URL"));
        cfg.setApiKey(System.getenv("CRONFLOW_AI_OLLAMA_KEY"));
        String model = System.getenv().getOrDefault("CRONFLOW_AI_MODEL", "qwen2.5-coder:7b");
        cfg.setDefaultModel(model);

        var provider = new OpenAiCompatibleProvider("ollama", cfg, RestClient.builder());
        var props = new CronflowAiProperties();
        props.setDefaultProvider("ollama");
        props.getBudget().setMaxRepairs(2);
        var generator = new AiDagGenerator(List.of(provider), props, new DagValidator(),
                new ObjectMapper());

        NodeCatalog catalog = NodeCatalog.of(List.of(
                new CatalogNode("fetchOrders", "Fetch orders for a date range", "fetchOrdersBean", "run",
                        List.of(), List.of("orders")),
                new CatalogNode("scoreOrders", "Score each order for risk", "scoreBean", "run",
                        List.of("orders"), List.of("scores")),
                new CatalogNode("notify", "Send a notification with the scores", "notifyBean", "run",
                        List.of("scores"), List.of())));

        DagCandidate c = generator.generate(
                "Fetch orders, then score them, then send a notification. Fetch runs first.", catalog,
                "ollama", true);

        System.out.println("[live] model=" + model + " valid=" + c.valid() + " repairs=" + c.repairs()
                + " promptTokens=" + c.promptTokens() + " completionTokens=" + c.completionTokens()
                + " error=" + c.error());
        if (c.dag() != null) {
            System.out.println("[live] graph=" + c.dag().graph() + " nodes="
                    + (c.dag().nodes() == null ? 0 : c.dag().nodes().size()) + " edges="
                    + (c.dag().edges() == null ? 0 : c.dag().edges().size()));
        }
        if (!c.issues().isEmpty()) {
            System.out.println("[live] issues=" + c.issues());
        }

        // The HTTP path and JSON parse must work. Graph validity is logged; small models vary.
        assertThat(c.error()).as("transport/provider error").isNull();
        assertThat(c.dag()).as("a DAG was parsed").isNotNull();
        assertThat(c.dag().graph()).isNotBlank();
    }
}
