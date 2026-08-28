package com.github.cronsmith.springapp.scheduler.test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 
 * The databases the JOOQ task manager is tested against.
 * 
 * <p>
 * H2 and SQLite run embedded and are always available. MySQL and PostgreSQL connect to a local
 * server; their credentials come from system properties so a CI machine can point them elsewhere,
 * falling back to the developer defaults documented in the README. A database whose server is not
 * reachable is skipped rather than failed, so the suite still runs on a laptop with only some of
 * them installed.
 * 
 * @Description: DatabaseProvider
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
enum DatabaseProvider {

    H2("db/cronsmith-schema-h2.sql") {
        @Override
        DataSource createDataSource() {
            // A named in-memory database kept alive by one held-open connection, so every pooled
            // connection sees the same tables. Randomised per instance to isolate test classes.
            String name = "cronsmith_" + System.nanoTime();
            return hikari("org.h2.Driver",
                    "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=LEGACY", "sa", "");
        }
    },

    SQLITE("db/cronsmith-schema-sqlite.sql") {
        @Override
        DataSource createDataSource() {
            // A shared in-memory SQLite database. A single connection in the pool, because SQLite
            // serialises writers anyway and separate in-memory connections would not share tables.
            HikariConfig config = baseConfig("org.sqlite.JDBC",
                    "jdbc:sqlite:file:cronsmith_" + System.nanoTime()
                            + "?mode=memory&cache=shared",
                    null, null);
            config.setMaximumPoolSize(1);
            return new HikariDataSource(config);
        }
    },

    POSTGRESQL("db/cronsmith-schema-postgresql.sql") {
        @Override
        DataSource createDataSource() {
            String host = property("cronsmith.pg.host", "localhost");
            String port = property("cronsmith.pg.port", "5432");
            String database = property("cronsmith.pg.database", "demo");
            String schema = property("cronsmith.pg.schema", "test");
            String user = property("cronsmith.pg.user", "fengy");
            String password = property("cronsmith.pg.password", "123456");
            String url = "jdbc:postgresql://" + host + ":" + port + "/" + database
                    + "?currentSchema=" + schema;
            DataSource dataSource =
                    hikari("org.postgresql.Driver", url, user, password);
            // The task tables live in the configured schema; make sure it exists before the DDL
            // that assumes it does.
            execute(dataSource, "CREATE SCHEMA IF NOT EXISTS " + schema);
            return dataSource;
        }
    },

    MYSQL("db/cronsmith-schema-mysql.sql") {
        @Override
        DataSource createDataSource() {
            String host = property("cronsmith.mysql.host", "localhost");
            String port = property("cronsmith.mysql.port", "3306");
            String database = property("cronsmith.mysql.database", "test");
            String user = property("cronsmith.mysql.user", "fengy");
            String password = property("cronsmith.mysql.password", "12345678");
            return hikari("com.mysql.cj.jdbc.Driver",
                    "jdbc:mysql://" + host + ":" + port + "/" + database + "?"
                            + MYSQL_PARAMS,
                    user, password);
        }
    };

    /**
     * connectionTimeZone=UTC with preserveInstants=false stops Connector/J from shifting a
     * LocalDateTime between the JVM zone and the server zone: the wall-clock value is stored and
     * read back unchanged, which is what a zone-less fire time needs. Without this a stored fire
     * time is silently offset by the JVM's zone, and only looks correct because the same offset is
     * undone on read.
     */
    private static final String MYSQL_PARAMS =
            "connectionTimeZone=UTC&preserveInstants=false&allowPublicKeyRetrieval=true&useSSL=false";

    private final String schemaResource;

    DatabaseProvider(String schemaResource) {
        this.schemaResource = schemaResource;
    }

    /**
     * A pooled data source for this database. Creating one for an unreachable server throws; use
     * {@link #isAvailable()} to decide whether to try.
     */
    abstract DataSource createDataSource();

    String getSchemaResource() {
        return schemaResource;
    }

    /**
     * Whether a connection can be opened. Embedded databases are always available; a remote one is
     * probed once.
     */
    boolean isAvailable() {
        try {
            DataSource dataSource = createDataSource();
            try (Connection connection = dataSource.getConnection()) {
                connection.isValid(2);
            }
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Loads this database's schema, dropping and recreating the tables so each run starts clean.
     */
    void initializeSchema(DataSource dataSource) throws SQLException, IOException {
        SqlScriptRunner.runScript(dataSource, schemaResource);
    }

    private static void execute(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot run: " + sql, e);
        }
    }

    private static DataSource hikari(String driver, String url, String user, String password) {
        return new HikariDataSource(baseConfig(driver, url, user, password));
    }

    private static HikariConfig baseConfig(String driver, String url, String user,
            String password) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setJdbcUrl(url);
        if (user != null) {
            config.setUsername(user);
        }
        if (password != null) {
            config.setPassword(password);
        }
        config.setMaximumPoolSize(4);
        config.setConnectionTimeout(3000L);
        config.setInitializationFailTimeout(-1L);
        return config;
    }

    private static String property(String key, String defaultValue) {
        return System.getProperty(key, defaultValue);
    }

}
