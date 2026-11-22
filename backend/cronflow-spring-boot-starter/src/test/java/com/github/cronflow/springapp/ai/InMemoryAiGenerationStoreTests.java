package com.github.cronflow.springapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InMemoryAiGenerationStoreTests {

    private AiGenerationRecord draft(String id, LocalDateTime at) {
        return new AiGenerationRecord(id, "make a flow", "ollama", "m", AiGenerationRecord.DRAFT, "flow",
                null, "{}", null, 10, 5, 0, null, null, at, null, null);
    }

    @Test
    void saveAndFind() {
        var store = new InMemoryAiGenerationStore();
        store.save(draft("a", LocalDateTime.now()));
        assertThat(store.find("a")).isPresent();
        assertThat(store.find("a").get().status()).isEqualTo(AiGenerationRecord.DRAFT);
        assertThat(store.find("missing")).isEmpty();
    }

    @Test
    void approveFlipsStatusAndStampsApprover() {
        var store = new InMemoryAiGenerationStore();
        store.save(draft("a", LocalDateTime.now()));
        store.approve("a", "alice", "billing", "flow");
        var r = store.find("a").orElseThrow();
        assertThat(r.status()).isEqualTo(AiGenerationRecord.APPROVED);
        assertThat(r.approvedBy()).isEqualTo("alice");
        assertThat(r.application()).isEqualTo("billing");
        assertThat(r.approvedAt()).isNotNull();
    }

    @Test
    void recentIsNewestFirstAndBounded() {
        var store = new InMemoryAiGenerationStore();
        store.save(draft("old", LocalDateTime.now().minusHours(2)));
        store.save(draft("new", LocalDateTime.now()));
        var recent = store.recent(1);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).id()).isEqualTo("new");
    }
}
