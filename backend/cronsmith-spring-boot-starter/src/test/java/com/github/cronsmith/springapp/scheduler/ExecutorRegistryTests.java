package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.ExecutorRegistry.ExecutorInstance;

/**
 * Unit tests for the in-memory executor list: dedup on upsert, round-robin pick within an
 * application, TTL eviction, and isolation between applications. Time is controlled by feeding
 * instances with explicit {@code lastSeen} through {@link ExecutorRegistry#apply}, so no sleeps and
 * no wall-clock flakiness.
 *
 * @Description: ExecutorRegistryTests
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
class ExecutorRegistryTests {

    private static final long TTL = 90_000L;

    @Test
    void upsertIsIdempotentPerInstanceId() {
        ExecutorRegistry registry = new ExecutorRegistry(TTL);
        registry.upsert("appA", "i1", "http://a/run", "http://a/health", 1);
        registry.upsert("appA", "i1", "http://a2/run", "http://a2/health", 1);
        // Same instanceId -> one entry, latest url wins.
        assertThat(registry.snapshot()).hasSize(1);
        assertThat(registry.snapshot().get(0).runUrl()).isEqualTo("http://a2/run");
    }

    @Test
    void pickCyclesRoundRobinAcrossLiveInstances() {
        ExecutorRegistry registry = new ExecutorRegistry(TTL);
        registry.upsert("appA", "i1", "http://i1/run", null, 1);
        registry.upsert("appA", "i2", "http://i2/run", null, 1);

        Set<String> firstTwo = new HashSet<>();
        firstTwo.add(registry.pick("appA", null).orElseThrow().instanceId());
        firstTwo.add(registry.pick("appA", null).orElseThrow().instanceId());
        // Two consecutive picks hit both instances -> load is spread, not pinned.
        assertThat(firstTwo).containsExactlyInAnyOrder("i1", "i2");
        // And it wraps back around.
        assertThat(registry.pick("appA", null).orElseThrow().instanceId()).isIn("i1", "i2");
    }

    @Test
    void pickIsScopedToTheApplication() {
        ExecutorRegistry registry = new ExecutorRegistry(TTL);
        registry.upsert("appA", "i1", "http://a/run", null, 1);
        registry.upsert("appB", "i2", "http://b/run", null, 1);
        assertThat(registry.pick("appA", null).orElseThrow().instanceId()).isEqualTo("i1");
        assertThat(registry.pick("appB", null).orElseThrow().instanceId()).isEqualTo("i2");
        assertThat(registry.pick("missing", null)).isEmpty();
    }

    @Test
    void pickSkipsInstancesOlderThanTtl() {
        ExecutorRegistry registry = new ExecutorRegistry(TTL);
        long stale = System.currentTimeMillis() - TTL - 10_000L;
        registry.apply(new ExecutorInstance("appA", "old", "http://old/run", null, stale, 1));
        assertThat(registry.pick("appA", null)).isEmpty();

        long fresh = System.currentTimeMillis();
        registry.apply(new ExecutorInstance("appA", "new", "http://new/run", null, fresh, 1));
        assertThat(registry.pick("appA", null).orElseThrow().instanceId()).isEqualTo("new");
    }

    @Test
    void evictStaleDropsExpiredButKeepsLive() {
        ExecutorRegistry registry = new ExecutorRegistry(TTL);
        long stale = System.currentTimeMillis() - TTL - 10_000L;
        registry.apply(new ExecutorInstance("appA", "old", "http://old/run", null, stale, 1));
        registry.apply(new ExecutorInstance("appA", "live", "http://live/run", null,
                System.currentTimeMillis(), 1));

        registry.evictStale();

        assertThat(registry.snapshot()).extracting(ExecutorInstance::instanceId)
                .containsExactly("live");
    }
}
