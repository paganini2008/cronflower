package com.github.cronflow.springapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import com.github.cronflow.springapp.dag.DagExecutorRegistry;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagGraph;

/**
 * Covers the small top-level cronflow server pieces: the engine-defaults environment post-processor,
 * the health indicator, the server properties and the constants.
 */
class CronflowMiscTests {

    @Test
    void engineDefaultsAddTheMultiprocessingSwitchesAtLowestPriority() {
        StandardEnvironment env = new StandardEnvironment();
        new CronflowEngineDefaults().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.spreader.multiprocessing.pooling.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.spreader.multiprocessing.dag.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.spreader.multiprocessing.aggregation.enabled"))
                .isEqualTo("true");
        // idempotent: a second pass does not fail or duplicate the source
        new CronflowEngineDefaults().postProcessEnvironment(env, null);
        assertThat(env.getPropertySources().contains("cronflowEngineDefaults")).isTrue();
    }

    @Test
    void engineDefaultsSkippedWhenServerDisabled() {
        StandardEnvironment env = new StandardEnvironment();
        env.getSystemProperties().put("cronflow.server.enabled", "false");
        try {
            new CronflowEngineDefaults().postProcessEnvironment(env, null);
            assertThat(env.getProperty("spring.spreader.multiprocessing.dag.enabled")).isNull();
        } finally {
            env.getSystemProperties().remove("cronflow.server.enabled");
        }
    }

    @Test
    void healthIndicatorReportsFeatureAndGraphCount() {
        DagExecutorRegistry registry = mock(DagExecutorRegistry.class);
        DagDefinition def = new DagDefinition("g", List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        when(registry.graphs()).thenReturn(List.of(new DagGraph("app", def)));
        CronflowHealthIndicator indicator = new CronflowHealthIndicator(registry);
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(indicator.health().getDetails()).containsEntry("feature", "cronflow")
                .containsEntry("graphs", 1);
    }

    @Test
    void serverPropertiesDefaultsAndSetters() {
        CronflowServerProperties props = new CronflowServerProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getApiPrefix()).isEqualTo(CronflowConstants.DAG_API_PREFIX);
        assertThat(props.getDefinitionFormat()).isEqualTo("json");
        assertThat(props.getDefaultNodeTimeoutMillis()).isEqualTo(30000L);
        assertThat(props.getExecutorTtlSeconds()).isEqualTo(60L);
        props.setEnabled(false);
        props.setApiPrefix("/x");
        props.setDefinitionFormat("yaml");
        props.setDefaultNodeTimeoutMillis(5L);
        props.setExecutorTtlSeconds(10L);
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getApiPrefix()).isEqualTo("/x");
        assertThat(props.getDefinitionFormat()).isEqualTo("yaml");
        assertThat(props.getDefaultNodeTimeoutMillis()).isEqualTo(5L);
        assertThat(props.getExecutorTtlSeconds()).isEqualTo(10L);
    }

    @Test
    void constants() {
        assertThat(CronflowConstants.API_BASE).isEqualTo("/cronflow");
        assertThat(CronflowConstants.SCHEDULER_API_PREFIX).isEqualTo("/cronflow/scheduler");
        assertThat(CronflowConstants.DAG_API_PREFIX).isEqualTo("/cronflow/dag");
        assertThat(CronflowConstants.SWAGGER_GROUP_SCHEDULER).isEqualTo("scheduler");
        assertThat(CronflowConstants.SWAGGER_GROUP_DAG).isEqualTo("dag");
    }
}
