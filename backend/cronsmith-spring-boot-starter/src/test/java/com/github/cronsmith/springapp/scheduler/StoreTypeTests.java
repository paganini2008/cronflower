package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The store kinds carry their nature intrinsically (node-local stores replicate and never shard;
 * networked servers are shared), detection maps JDBC product names case-insensitively via the alias
 * map, an unknown product falls back to OTHER, and new kinds can be registered.
 *
 * @Description: StoreTypeTests
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
class StoreTypeTests {

    @Test
    void nodeLocalKindsReplicateAndDoNotShard() {
        for (StoreType t : List.of(StoreType.IN_MEMORY, StoreType.SQLITE, StoreType.H2)) {
            assertThat(t.isReplicated()).as("%s replicates", t.name()).isTrue();
            assertThat(t.isShared()).as("%s is not shared", t.name()).isFalse();
        }
        assertThat(StoreType.IN_MEMORY.isInMemory()).isTrue();
        assertThat(StoreType.SQLITE.isInMemory()).isFalse();
    }

    @Test
    void networkedKindsAreSharedAndDoNotReplicate() {
        for (StoreType t : List.of(StoreType.MYSQL, StoreType.POSTGRESQL, StoreType.ORACLE,
                StoreType.SQLSERVER, StoreType.OTHER)) {
            assertThat(t.isShared()).as("%s is shared", t.name()).isTrue();
            assertThat(t.isReplicated()).as("%s does not replicate", t.name()).isFalse();
        }
    }

    @Test
    void detectionMapsProductNamesCaseInsensitively() {
        assertThat(StoreType.fromProductName("MySQL")).isSameAs(StoreType.MYSQL);
        assertThat(StoreType.fromProductName("MariaDB")).isSameAs(StoreType.MYSQL);
        assertThat(StoreType.fromProductName("PostgreSQL")).isSameAs(StoreType.POSTGRESQL);
        assertThat(StoreType.fromProductName("SQLite")).isSameAs(StoreType.SQLITE);
        assertThat(StoreType.fromProductName("H2")).isSameAs(StoreType.H2);
        assertThat(StoreType.fromProductName("Oracle")).isSameAs(StoreType.ORACLE);
        assertThat(StoreType.fromProductName("Microsoft SQL Server")).isSameAs(StoreType.SQLSERVER);
    }

    @Test
    void unknownProductFallsBackToOther() {
        assertThat(StoreType.fromProductName("SomeNewDB")).isSameAs(StoreType.OTHER);
        assertThat(StoreType.fromProductName(null)).isSameAs(StoreType.OTHER);
    }

    @Test
    void byNameIsCaseInsensitive() {
        assertThat(StoreType.byName("mysql")).isSameAs(StoreType.MYSQL);
        assertThat(StoreType.byName("MYSQL")).isSameAs(StoreType.MYSQL);
        assertThat(StoreType.byName("In_Memory")).isSameAs(StoreType.IN_MEMORY);
        assertThat(StoreType.byName("nope")).isNull();
    }

    @Test
    void builtInKindsAreClassified() {
        assertThat(StoreType.MYSQL.metadata("kind")).isEqualTo("server");
        assertThat(StoreType.POSTGRESQL.metadata("kind")).isEqualTo("server");
        assertThat(StoreType.SQLITE.metadata("kind")).isEqualTo("embedded");
        assertThat(StoreType.H2.metadata("kind")).isEqualTo("embedded");
        assertThat(StoreType.IN_MEMORY.metadata("kind")).isEqualTo("memory");
    }

    @Test
    void detectReadsLiveConnectionMetadata() {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:detect_meta;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        StoreType detected = StoreType.detect(ds);
        // Right kind (equals by name — the returned instance is a metadata-carrying copy of H2)…
        assertThat(detected).isEqualTo(StoreType.H2);
        assertThat(detected.isReplicated()).isTrue();
        assertThat(detected.isShared()).isFalse();
        // …carrying what the JDBC DatabaseMetaData reported.
        assertThat(detected.metadata("productName")).containsIgnoringCase("h2");
        assertThat(detected.metadata("kind")).isEqualTo("embedded");
        assertThat(detected.metadata("driverName")).isNotBlank();
    }

    @Test
    void aNewKindCanBeRegistered() {
        StoreType cockroach = StoreType.register("COCKROACH", false, true, List.of("cockroachdb"),
                java.util.Map.of("kind", "distributed-sql"));
        assertThat(cockroach.isShared()).isTrue();
        assertThat(StoreType.fromProductName("CockroachDB")).isSameAs(cockroach);
        assertThat(StoreType.byName("cockroach")).isSameAs(cockroach);
        assertThat(cockroach.metadata()).containsEntry("kind", "distributed-sql");
    }
}
