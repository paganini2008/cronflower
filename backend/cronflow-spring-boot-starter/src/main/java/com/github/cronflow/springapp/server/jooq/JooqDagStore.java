package com.github.cronflow.springapp.server.jooq;

import com.github.cronflow.springapp.server.DagStore;
import com.github.cronflow.springapp.server.DagIds;
import com.github.cronflow.springapp.server.pojo.StoredDag;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.tools.jdbc.JDBCUtils;

/**
 * jOOQ-backed {@link DagStore} for {@code cf_task_dag}, the jOOQ counterpart of {@link JpaDagStore} —
 * so cronflow aligns with cronsmith's JPA/jOOQ storage choice and works in a jOOQ-only deployment.
 *
 * <p>
 * No hand-written, dialect-specific SQL: the table is created via jOOQ's portable
 * {@code createTableIfNotExists} and all CRUD is dialect-agnostic, so the same code runs on H2 /
 * SQLite / MySQL / PostgreSQL / SQL Server / Oracle.
 *
 * @Description: JooqDagStore
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class JooqDagStore implements DagStore {

    private static final String TABLE = "cf_task_dag";

    private final DSLContext dsl;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JooqDagStore.class);

    public JooqDagStore(DataSource dataSource) {
        SQLDialect dialect = detectDialect(dataSource);
        this.dsl = DSL.using(dataSource, dialect);
        ensureSchema();
        log.info("cronflow: cf_task_dag store = jOOQ (dialect {})", dialect);
    }

    private static SQLDialect detectDialect(DataSource dataSource) {
        try (Connection c = dataSource.getConnection()) {
            return JDBCUtils.dialect(c);
        } catch (Exception e) {
            throw new IllegalStateException("cronflow: cannot determine SQL dialect", e);
        }
    }

    private void ensureSchema() {
        dsl.createTableIfNotExists(DSL.table(DSL.unquotedName(TABLE)))
                .column(DSL.field(DSL.unquotedName("id"), SQLDataType.VARCHAR(512).nullable(false)))
                .column(DSL.field(DSL.unquotedName("application"), SQLDataType.VARCHAR(255)))
                .column(DSL.field(DSL.unquotedName("graph_name"), SQLDataType.VARCHAR(255)))
                .column(DSL.field(DSL.unquotedName("definition"), SQLDataType.CLOB))
                .column(DSL.field(DSL.unquotedName("format"), SQLDataType.VARCHAR(16)))
                .column(DSL.field(DSL.unquotedName("enabled"), SQLDataType.BOOLEAN))
                .column(DSL.field(DSL.unquotedName("last_modified"), SQLDataType.LOCALDATETIME))
                .constraints(DSL.constraint("pk_" + TABLE).primaryKey(DSL.unquotedName("id")))
                .execute();
    }

    @Override
    public void save(String application, String graph, String definition, String format) {
        String id = DagIds.taskDagId(application, graph);
        int updated = dsl.update(DSL.table(DSL.unquotedName(TABLE)))
                .set(DSL.field(DSL.unquotedName("definition")), definition)
                .set(DSL.field(DSL.unquotedName("format")), format)
                .set(DSL.field(DSL.unquotedName("enabled")), (Object) Boolean.TRUE)
                .set(DSL.field(DSL.unquotedName("last_modified")), (Object) LocalDateTime.now())
                .set(DSL.field(DSL.unquotedName("application")), application)
                .set(DSL.field(DSL.unquotedName("graph_name")), graph)
                .where(DSL.field(DSL.unquotedName("id"), String.class).eq(id))
                .execute();
        if (updated == 0) {
            dsl.insertInto(DSL.table(DSL.unquotedName(TABLE)),
                    DSL.field(DSL.unquotedName("id")), DSL.field(DSL.unquotedName("application")),
                    DSL.field(DSL.unquotedName("graph_name")), DSL.field(DSL.unquotedName("definition")),
                    DSL.field(DSL.unquotedName("format")), DSL.field(DSL.unquotedName("enabled")),
                    DSL.field(DSL.unquotedName("last_modified")))
                    .values(id, application, graph, definition, format, Boolean.TRUE,
                            LocalDateTime.now())
                    .execute();
        }
    }

    @Override
    public List<StoredDag> loadAll() {
        List<StoredDag> out = new ArrayList<>();
        for (Record r : dsl.select(DSL.field(DSL.unquotedName("application")),
                DSL.field(DSL.unquotedName("graph_name")), DSL.field(DSL.unquotedName("definition")),
                DSL.field(DSL.unquotedName("format"))).from(DSL.table(DSL.unquotedName(TABLE))).fetch()) {
            out.add(new StoredDag(r.get("application", String.class), r.get("graph_name", String.class),
                    r.get("definition", String.class), r.get("format", String.class)));
        }
        return out;
    }

}
