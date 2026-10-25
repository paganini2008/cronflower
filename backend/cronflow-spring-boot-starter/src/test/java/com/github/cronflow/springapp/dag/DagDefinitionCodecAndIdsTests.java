package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.cronflow.springapp.pojo.DagDefinition;
import tools.jackson.databind.ObjectMapper;

/**
 * Round-trips a {@link DagDefinition} through the codec in both JSON and YAML (this project uses
 * Jackson 3, {@code tools.jackson}), checks the format normalisation, and covers {@link DagIds}.
 */
class DagDefinitionCodecAndIdsTests {

    private final DagDefinitionCodec codec = new DagDefinitionCodec(new ObjectMapper());

    private DagDefinition sample() {
        return new DagDefinition("order-flow", List.of("orderId"),
                List.of(new DagDefinition.ChannelDef("steps", "concatList")),
                List.of(new DagDefinition.NodeDef("validate", true, "ALL", 0, "orderFlow", "validate",
                        "")),
                List.of(new DagDefinition.EdgeDef("validate", "charge", "ON_SUCCESS", null)),
                List.of(new DagDefinition.ConditionalDef(List.of("validate"), "predicate", null,
                        List.of("#ok"), Map.of("0", List.of("charge")), List.of("flag"))),
                List.of(new DagDefinition.ShardDef("validate", "in", "out", 2)));
    }

    @Test
    void jsonRoundTrip() {
        String json = codec.encode(sample(), DagDefinitionCodec.JSON);
        assertThat(json).contains("order-flow");
        DagDefinition back = codec.decode(json, DagDefinitionCodec.JSON);
        assertThat(back).isEqualTo(sample());
    }

    @Test
    void yamlRoundTrip() {
        String yaml = codec.encode(sample(), DagDefinitionCodec.YAML);
        assertThat(yaml).contains("order-flow");
        DagDefinition back = codec.decode(yaml, DagDefinitionCodec.YAML);
        assertThat(back).isEqualTo(sample());
    }

    @Test
    void unknownFormatFallsBackToJson() {
        // encode with an unknown format uses the JSON mapper; decode the same way.
        String text = codec.encode(sample(), "xml");
        DagDefinition back = codec.decode(text, "xml");
        assertThat(back).isEqualTo(sample());
    }

    @Test
    void normalizeDefaultsNonYamlToJson() {
        assertThat(codec.normalize("yaml")).isEqualTo(DagDefinitionCodec.YAML);
        assertThat(codec.normalize("YAML")).isEqualTo(DagDefinitionCodec.YAML);
        assertThat(codec.normalize("json")).isEqualTo(DagDefinitionCodec.JSON);
        assertThat(codec.normalize("anything")).isEqualTo(DagDefinitionCodec.JSON);
        assertThat(codec.normalize(null)).isEqualTo(DagDefinitionCodec.JSON);
    }

    @Test
    void dagIdsCompose() {
        assertThat(DagIds.taskDagId("app", "graph")).isEqualTo("app/graph");
        assertThat(DagIds.nodeLogId("run-1", 5)).isEqualTo("run-1#5");
    }
}
