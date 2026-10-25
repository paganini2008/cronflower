package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagGraph;
import com.github.cronflow.springapp.pojo.DagHeartbeatRequest;
import com.github.cronflow.springapp.pojo.DagRegistrationRequest;
import com.github.cronflow.springapp.pojo.StoredDag;
import com.github.cronflow.springapp.pojo.TriggerBinding;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers the in-memory DAG registry: registration (with and without a supplied instance id),
 * persistence to the store, heartbeat liveness, definition lookup from cache and from the store,
 * registry warm-up, round-robin host picking and the console query helpers. Uses an in-memory fake
 * store (never a database).
 */
class DagExecutorRegistryTests {

    private final DagDefinitionCodec codec = new DagDefinitionCodec(new ObjectMapper());

    /** An in-memory {@link DagStore}; loadAll can be made to throw to cover the warn branch. */
    private static final class FakeStore implements DagStore {
        final List<StoredDag> rows = new ArrayList<>();
        boolean failLoad;
        boolean failSave;

        @Override
        public void save(String application, String graph, String definition, String format) {
            if (failSave) {
                throw new IllegalStateException("save boom");
            }
            rows.removeIf(r -> r.graph().equals(graph) && r.application().equals(application));
            rows.add(new StoredDag(application, graph, definition, format));
        }

        @Override
        public List<StoredDag> loadAll() {
            if (failLoad) {
                throw new IllegalStateException("load boom");
            }
            return List.copyOf(rows);
        }
    }

    private DagDefinition def(String graph) {
        return new DagDefinition(graph, List.of(), List.of(),
                List.of(new DagDefinition.NodeDef("n", true, "ALL", 0, "b", "m", "")),
                List.of(), List.of(), List.of());
    }

    private DagRegistrationRequest reg(String instanceId, String graph) {
        return new DagRegistrationRequest("app", instanceId, "http://run/" + graph, "http://health",
                List.of(def(graph)), List.of(new TriggerBinding("grp", "task-" + graph, graph)), 2);
    }

    private DagExecutorRegistry registry(FakeStore store) {
        return new DagExecutorRegistry(store, codec, "json", 60L);
    }

    @Test
    void registerPersistsAndTracksHostsAndTriggers() {
        FakeStore store = new FakeStore();
        DagExecutorRegistry registry = registry(store);

        String id = registry.register(reg("i1", "g1"));
        assertThat(id).isEqualTo("i1");
        assertThat(registry.definition("g1")).isEqualTo(def("g1"));
        assertThat(registry.applicationOf("g1")).contains("app");
        assertThat(registry.triggeredGraph("grp", "task-g1")).contains("g1");
        assertThat(registry.triggeredGraph("grp", "unknown")).isEmpty();
        assertThat(store.rows).hasSize(1);
        assertThat(registry.pick("g1")).isPresent();
        assertThat(registry.liveApplications()).containsExactly("app");
        assertThat(registry.anyLiveInstance("app")).isPresent();
        assertThat(registry.anyLiveInstance("other")).isEmpty();
    }

    @Test
    void registerWithoutInstanceIdAssignsOne() {
        DagExecutorRegistry registry = registry(new FakeStore());
        String id = registry.register(new DagRegistrationRequest("app", "  ", "run", "health",
                List.of(def("g")), null, null));
        assertThat(id).isNotBlank();
        // null weight defaults to 1 and a null triggers list is tolerated.
        assertThat(registry.anyLiveInstance("app")).get()
                .extracting(DagExecutorRegistry.ExecutorInstance::weight).isEqualTo(1);
    }

    @Test
    void heartbeatKnownAndUnknown() {
        DagExecutorRegistry registry = registry(new FakeStore());
        registry.register(reg("i1", "g1"));
        assertThat(registry.heartbeat(new DagHeartbeatRequest("app", "i1", "run2", "health2", 5)))
                .isTrue();
        assertThat(registry.heartbeat(new DagHeartbeatRequest("app", "ghost", "run", "health", null)))
                .isFalse();
        // null weight on heartbeat keeps the previous weight.
        assertThat(registry.heartbeat(new DagHeartbeatRequest("app", "i1", "run", "health", null)))
                .isTrue();
    }

    @Test
    void definitionFallsBackToStoreWhenNotCached() {
        FakeStore store = new FakeStore();
        store.rows.add(new StoredDag("app", "stored", codec.encode(def("stored"), "json"), "json"));
        DagExecutorRegistry registry = registry(store);
        // not registered in memory, but present in the store
        assertThat(registry.definition("stored")).isEqualTo(def("stored"));
        assertThat(registry.applicationOf("stored")).contains("app");
        assertThat(registry.definition("missing")).isNull();
    }

    @Test
    void definitionReturnsNullWhenStoreLookupThrows() {
        FakeStore store = new FakeStore();
        store.failLoad = true;
        assertThat(registry(store).definition("x")).isNull();
    }

    @Test
    void hydrateFromStoreLoadsDecodableAndSkipsBad() {
        FakeStore store = new FakeStore();
        store.rows.add(new StoredDag("app", "good", codec.encode(def("good"), "json"), "json"));
        store.rows.add(new StoredDag("app", "bad", "not json at all", "json"));
        DagExecutorRegistry registry = registry(store);
        assertThat(registry.hydrateFromStore()).isEqualTo(1);
        assertThat(registry.definition("good")).isEqualTo(def("good"));
    }

    @Test
    void hydrateFromStoreToleratesStoreFailure() {
        FakeStore store = new FakeStore();
        store.failLoad = true;
        assertThat(registry(store).hydrateFromStore()).isZero();
    }

    @Test
    void persistFailureIsSwallowed() {
        FakeStore store = new FakeStore();
        store.failSave = true;
        DagExecutorRegistry registry = registry(store);
        // register still succeeds although persistence throws
        assertThat(registry.register(reg("i1", "g1"))).isEqualTo("i1");
        assertThat(registry.definition("g1")).isEqualTo(def("g1"));
    }

    @Test
    void graphsAreSortedByName() {
        DagExecutorRegistry registry = registry(new FakeStore());
        registry.register(reg("i1", "zebra"));
        registry.register(reg("i1", "apple"));
        List<DagGraph> graphs = registry.graphs();
        assertThat(graphs).extracting(g -> g.definition().graph()).containsExactly("apple", "zebra");
    }

    @Test
    void pickRoundRobinsAcrossLiveHostsAndIsEmptyForUnknownGraph() {
        DagExecutorRegistry registry = registry(new FakeStore());
        registry.register(reg("i1", "g1"));
        registry.register(reg("i2", "g1"));
        AtomicInteger i1 = new AtomicInteger();
        AtomicInteger i2 = new AtomicInteger();
        for (int i = 0; i < 6; i++) {
            String id = registry.pick("g1").orElseThrow().instanceId();
            if (id.equals("i1")) {
                i1.incrementAndGet();
            } else {
                i2.incrementAndGet();
            }
        }
        assertThat(i1.get()).isEqualTo(3);
        assertThat(i2.get()).isEqualTo(3);
        assertThat(registry.pick("nope")).isEmpty();
    }

    @Test
    void staleInstancesAreNotLive() throws Exception {
        FakeStore store = new FakeStore();
        DagExecutorRegistry registry = new DagExecutorRegistry(store, codec, "json", 0L);
        registry.register(reg("i1", "g1"));
        Thread.sleep(1100L); // ttl is 0s, so after >1s the instance is stale
        assertThat(registry.pick("g1")).isEmpty();
        assertThat(registry.liveApplications()).isEmpty();
        assertThat(registry.anyLiveInstance("app")).isEmpty();
    }

    @Test
    void executorInstanceRecordAccessors() {
        DagExecutorRegistry.ExecutorInstance inst = new DagExecutorRegistry.ExecutorInstance("app",
                "i1", "run", "health", 2, java.time.Instant.now());
        assertThat(inst.application()).isEqualTo("app");
        assertThat(inst.instanceId()).isEqualTo("i1");
        assertThat(inst.runUrl()).isEqualTo("run");
        assertThat(inst.healthCheckUrl()).isEqualTo("health");
        assertThat(inst.weight()).isEqualTo(2);
        assertThat(inst.lastSeen()).isNotNull();
    }
}
