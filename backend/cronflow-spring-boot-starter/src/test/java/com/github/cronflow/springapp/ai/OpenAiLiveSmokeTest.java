package com.github.cronflow.springapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import com.github.cronflow.springapp.ai.CronflowAiProperties.Provider;
import tools.jackson.databind.ObjectMapper;

/**
 * Live smoke against the OpenAI cloud. Off by default; enable with CRONFLOW_AI_OPENAI_LIVE=true and
 * OPENAI_API_KEY in the environment (CRONFLOW_AI_OPENAI_MODEL optional, defaults gpt-4o-mini). It drives
 * the real provider with kind=openai and a non-sensitive synthetic prompt and catalog. No secret is
 * stored here; the key comes from the environment.
 */
@EnabledIfEnvironmentVariable(named = "CRONFLOW_AI_OPENAI_LIVE", matches = "true")
class OpenAiLiveSmokeTest {

    @Test
    void generatesADagViaOpenAiCloud() {
        Provider cfg = new Provider();
        cfg.setKind("openai");           // base-url defaults to https://api.openai.com
        cfg.setApiKey(System.getenv("OPENAI_API_KEY"));
        String model = System.getenv().getOrDefault("CRONFLOW_AI_OPENAI_MODEL", "gpt-4o-mini");
        cfg.setDefaultModel(model);
        cfg.setAllowCloud(true);

        var provider = new OpenAiCompatibleProvider("openai", cfg, RestClient.builder());
        assertThat(provider.cloud()).isTrue();

        var props = new CronflowAiProperties();
        props.setDefaultProvider("openai");
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
                "openai", false);

        System.out.println("[live-openai] model=" + model + " valid=" + c.valid() + " repairs="
                + c.repairs() + " promptTokens=" + c.promptTokens() + " completionTokens="
                + c.completionTokens() + " error=" + c.error());
        if (c.dag() != null) {
            System.out.println("[live-openai] graph=" + c.dag().graph() + " nodes="
                    + (c.dag().nodes() == null ? 0 : c.dag().nodes().size()) + " edges="
                    + (c.dag().edges() == null ? 0 : c.dag().edges().size()));
        }
        if (!c.issues().isEmpty()) {
            System.out.println("[live-openai] issues=" + c.issues());
        }

        assertThat(c.error()).as("transport/provider error").isNull();
        assertThat(c.dag()).as("a DAG was parsed").isNotNull();
        assertThat(c.dag().graph()).isNotBlank();
    }
}
