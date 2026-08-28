package com.github.cronsmith.springapp.scheduler;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import javax.sql.DataSource;

/**
 * The kind of store backing the distributed task manager. A distributed-only concept — the single-node
 * core knows nothing about it — so it lives here in the starter and is surfaced through
 * {@link MultipleStoreTaskManager}.
 *
 * <p>
 * Each instance carries the store's intrinsic nature, so only the legal combinations exist — a shared
 * server is never "replicated", a node-local store never "shared", and a contradiction cannot be
 * configured.
 *
 * <ul>
 * <li><b>replicated</b> — node-local: each node keeps its own copy, kept in sync by broadcasting every
 * write. True for the embedded/in-memory stores.</li>
 * <li><b>shared</b> — a single store every node reaches with a cluster-wide atomic compare-and-set;
 * the precondition for group sharding. True for the networked servers.</li>
 * </ul>
 *
 * <p>
 * The built-in kinds are exposed as constants ({@link #MYSQL}, {@link #SQLITE}, …). This is a type-safe
 * enum rather than a Java {@code enum} on purpose: {@link #register} lets an application add its own
 * kind (a NoSQL or a new JDBC database) and its product-name aliases at start-up, which a closed
 * {@code enum} could not.
 *
 * <p>
 * The store is not configured by hand: no DataSource means {@link #IN_MEMORY}; otherwise it is
 * {@linkplain #detect(DataSource) auto-detected} from the JDBC connection's product name — the same way
 * Hibernate/Flyway pick a dialect, via a {@linkplain #register alias map} rather than a chain of
 * {@code if}s.
 *
 * @Description: StoreType
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public final class StoreType {

    // Declared before the constants below, which register into them during class init. Keys are
    // case-insensitive, so callers never have to normalise case.
    private static final Map<String, StoreType> BY_NAME =
            new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    // Product-name keyword -> store type, for detection. No if/else chain.
    private static final Map<String, StoreType> BY_PRODUCT =
            new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    /** In-process memory; node-local. Used when there is no DataSource. */
    public static final StoreType IN_MEMORY =
            register("IN_MEMORY", true, false, List.of(), Map.of("kind", "memory"));
    /** Embedded SQLite file; node-local. */
    public static final StoreType SQLITE =
            register("SQLITE", true, false, List.of("sqlite"), Map.of("kind", "embedded"));
    /** Embedded H2; node-local (an H2 TCP server is actually shared). */
    public static final StoreType H2 =
            register("H2", true, false, List.of("h2"), Map.of("kind", "embedded"));
    /** Networked MySQL; a shared store. */
    public static final StoreType MYSQL = register("MYSQL", false, true,
            List.of("mysql", "mariadb"), Map.of("kind", "server"));
    /** Networked PostgreSQL; a shared store. */
    public static final StoreType POSTGRESQL =
            register("POSTGRESQL", false, true, List.of("postgresql"), Map.of("kind", "server"));
    /** Networked Oracle; a shared store. */
    public static final StoreType ORACLE =
            register("ORACLE", false, true, List.of("oracle"), Map.of("kind", "server"));
    /** Networked SQL Server; a shared store. */
    public static final StoreType SQLSERVER = register("SQLSERVER", false, true,
            List.of("sql server", "sqlserver"), Map.of("kind", "server"));
    /** Any other JDBC database, treated as a shared server — the auto-detection fallback. */
    public static final StoreType OTHER =
            register("OTHER", false, true, List.of(), Map.of("kind", "server"));

    private final String name;
    private final boolean replicated;
    private final boolean shared;
    private final Map<String, String> metadata;

    private StoreType(String name, boolean replicated, boolean shared, Map<String, String> metadata) {
        this.name = name;
        this.replicated = replicated;
        this.shared = shared;
        this.metadata = metadata;
    }

    /**
     * Define (or redefine) a store kind and register its detection aliases. Call at start-up to add a
     * kind the library does not ship, e.g.
     * {@code StoreType.register("COCKROACH", false, true, List.of("cockroachdb"))}.
     *
     * @param name           the kind's label, e.g. {@code MYSQL}
     * @param replicated     node-local, replicated via broadcast
     * @param shared         a single shared store (sharding-capable)
     * @param productAliases substrings of the JDBC product name that select this kind (case-insensitive)
     */
    public static StoreType register(String name, boolean replicated, boolean shared,
            List<String> productAliases) {
        return register(name, replicated, shared, productAliases, Map.of());
    }

    /** As {@link #register(String, boolean, boolean, List)}, with extra {@link #metadata()}. */
    public static StoreType register(String name, boolean replicated, boolean shared,
            List<String> productAliases, Map<String, String> metadata) {
        StoreType type = new StoreType(name, replicated, shared, Map.copyOf(metadata));
        BY_NAME.put(name, type);
        for (String alias : productAliases) {
            BY_PRODUCT.put(alias, type);
        }
        return type;
    }

    /** The label, e.g. {@code MYSQL}, {@code SQLITE}, {@code IN_MEMORY}. */
    public String name() {
        return name;
    }

    /** Whether each node keeps its own copy that writes are broadcast to. Node-local stores only. */
    public boolean isReplicated() {
        return replicated;
    }

    /** Whether it is a single shared store with cluster-wide CAS — the precondition for sharding. */
    public boolean isShared() {
        return shared;
    }

    /** Whether the store lives in this process' memory (no external store). */
    public boolean isInMemory() {
        return IN_MEMORY.equals(this);
    }

    /**
     * Extra, store-specific attributes. Empty for the built-in kinds; a hook so future store diversity
     * can be described (via {@link #register}) without widening this type or the
     * {@link MultipleStoreTaskManager} SPI.
     */
    public Map<String, String> metadata() {
        return metadata;
    }

    /** One metadata attribute (e.g. {@code kind}, {@code productVersion}, {@code driverName}), or null. */
    public String metadata(String key) {
        return metadata.get(key);
    }

    /**
     * A copy of this kind with extra metadata merged in (this kind's entries first, then {@code extra}).
     * Used by {@link #detect} to attach the live {@code DatabaseMetaData} of a connection without
     * mutating the shared constant.
     */
    public StoreType withMetadata(Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(this.metadata);
        merged.putAll(extra);
        return new StoreType(name, replicated, shared, Map.copyOf(merged));
    }

    /** Look up a registered kind by {@link #name()} (case-insensitive), or null if none. */
    public static StoreType byName(String name) {
        return name == null ? null : BY_NAME.get(name.trim());
    }

    /**
     * Detect the store from a live JDBC connection. Selects the kind by product name and attaches
     * whatever the connection's {@link DatabaseMetaData} can tell us (product/driver name and version,
     * URL, user) as {@link #metadata()}. Opens (and closes) one connection; each metadata getter is
     * read defensively, since some drivers throw on some of them.
     *
     * @throws IllegalStateException if the connection cannot be obtained
     */
    public static StoreType detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData md = connection.getMetaData();
            StoreType kind = fromProductName(read(md::getDatabaseProductName));
            Map<String, String> info = new LinkedHashMap<>();
            put(info, "productName", read(md::getDatabaseProductName));
            put(info, "productVersion", read(md::getDatabaseProductVersion));
            put(info, "driverName", read(md::getDriverName));
            put(info, "driverVersion", read(md::getDriverVersion));
            put(info, "url", read(md::getURL));
            put(info, "userName", read(md::getUserName));
            return info.isEmpty() ? kind : kind.withMetadata(info);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not detect the cronsmith store type from the DataSource", e);
        }
    }

    /** A {@link DatabaseMetaData} getter that may throw; used with {@link #read}. */
    @FunctionalInterface
    private interface MetaReader {
        String get() throws SQLException;
    }

    private static String read(MetaReader reader) {
        try {
            return reader.get();
        } catch (Exception e) {
            return null;
        }
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    /**
     * Map a JDBC {@code DatabaseMetaData.getDatabaseProductName()} to a store type via the alias map;
     * an unrecognized product is {@link #OTHER} (a shared server).
     */
    public static StoreType fromProductName(String productName) {
        if (productName != null) {
            String p = productName.toLowerCase();
            for (Map.Entry<String, StoreType> entry : BY_PRODUCT.entrySet()) {
                if (p.contains(entry.getKey().toLowerCase())) {
                    return entry.getValue();
                }
            }
        }
        return OTHER;
    }

    /** Two store types are the same kind when they share a {@link #name()} (metadata aside). */
    @Override
    public boolean equals(Object o) {
        return o instanceof StoreType && name.equalsIgnoreCase(((StoreType) o).name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

}
