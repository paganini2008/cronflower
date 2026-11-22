package com.github.cronflow.springapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Live DB smoke for {@link JooqAiGenerationStore}: proves the self-creating table and dialect CRUD work
 * on a real database. Off by default; enable with CRONFLOW_AI_DB=true. Connection comes from env
 * (CRONFLOW_AI_DB_URL/USER/PASS) or defaults to the local MySQL demo database. Drops the table at the
 * end so it leaves no trace.
 */
@EnabledIfEnvironmentVariable(named = "CRONFLOW_AI_DB", matches = "true")
class JooqAiGenerationStoreDbTest {

    @Test
    void crudOnRealDatabase() {
        String url = System.getenv().getOrDefault("CRONFLOW_AI_DB_URL",
                "jdbc:mysql://localhost:3306/demo?connectionTimeZone=UTC&preserveInstants=false"
                        + "&allowPublicKeyRetrieval=true&useSSL=false");
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(url);
        ds.setUsername(System.getenv().getOrDefault("CRONFLOW_AI_DB_USER", "fengy"));
        ds.setPassword(System.getenv().getOrDefault("CRONFLOW_AI_DB_PASS", "123456"));

        var store = new JooqAiGenerationStore(ds); // self-creates cf_ai_generation
        try {
            String id = UUID.randomUUID().toString();
            store.save(new AiGenerationRecord(id, "make a flow", "ollama", "qwen2.5-coder:7b",
                    AiGenerationRecord.DRAFT, "flow", null, "{\"graph\":\"flow\"}", null, 100, 50, 1,
                    null, "tester", LocalDateTime.now(), null, null));

            var found = store.find(id).orElseThrow();
            assertThat(found.prompt()).isEqualTo("make a flow");
            assertThat(found.status()).isEqualTo(AiGenerationRecord.DRAFT);
            assertThat(found.promptTokens()).isEqualTo(100);

            store.approve(id, "bob", "billing", "flow");
            var approved = store.find(id).orElseThrow();
            assertThat(approved.status()).isEqualTo(AiGenerationRecord.APPROVED);
            assertThat(approved.approvedBy()).isEqualTo("bob");
            assertThat(approved.application()).isEqualTo("billing");
            assertThat(approved.approvedAt()).isNotNull();

            assertThat(store.recent(10)).anyMatch(r -> r.id().equals(id));
            System.out.println("[live-db] cf_ai_generation CRUD ok on " + JDBCUtils.dialect(url));
        } finally {
            DSL.using(ds, JDBCUtils.dialect(url))
                    .dropTableIfExists(DSL.table(DSL.unquotedName("cf_ai_generation"))).execute();
        }
    }
}
