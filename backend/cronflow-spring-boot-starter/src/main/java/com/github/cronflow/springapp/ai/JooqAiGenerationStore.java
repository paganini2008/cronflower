/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cronflow.springapp.ai;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.tools.jdbc.JDBCUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jOOQ-backed {@link AiGenerationStore} for {@code cf_ai_generation}, mirroring the project's
 * {@code JooqDagStore}: the table is created with jOOQ's portable {@code createTableIfNotExists} and all
 * CRUD is dialect-agnostic, so it runs on H2 / SQLite / MySQL / PostgreSQL / SQL Server / Oracle without
 * any hand-written DDL.
 */
public class JooqAiGenerationStore implements AiGenerationStore {

    private static final Logger log = LoggerFactory.getLogger(JooqAiGenerationStore.class);
    private static final String TABLE = "cf_ai_generation";

    private final DSLContext dsl;

    public JooqAiGenerationStore(DataSource dataSource) {
        SQLDialect dialect = detectDialect(dataSource);
        this.dsl = DSL.using(dataSource, dialect);
        ensureSchema();
        log.info("cronflow.ai: cf_ai_generation store = jOOQ (dialect {})", dialect);
    }

    private static SQLDialect detectDialect(DataSource dataSource) {
        try (Connection c = dataSource.getConnection()) {
            return JDBCUtils.dialect(c);
        } catch (Exception e) {
            throw new IllegalStateException("cronflow.ai: cannot determine SQL dialect", e);
        }
    }

    private void ensureSchema() {
        dsl.createTableIfNotExists(DSL.table(DSL.unquotedName(TABLE)))
                .column(f("id"), SQLDataType.VARCHAR(512).nullable(false))
                .column(f("prompt"), SQLDataType.CLOB)
                .column(f("provider"), SQLDataType.VARCHAR(64))
                .column(f("model"), SQLDataType.VARCHAR(128))
                .column(f("status"), SQLDataType.VARCHAR(16))
                .column(f("graph_name"), SQLDataType.VARCHAR(255))
                .column(f("application"), SQLDataType.VARCHAR(255))
                .column(f("candidate"), SQLDataType.CLOB)
                .column(f("issues"), SQLDataType.CLOB)
                .column(f("prompt_tokens"), SQLDataType.INTEGER)
                .column(f("completion_tokens"), SQLDataType.INTEGER)
                .column(f("repairs"), SQLDataType.INTEGER)
                .column(f("error"), SQLDataType.CLOB)
                .column(f("created_by"), SQLDataType.VARCHAR(255))
                .column(f("created_at"), SQLDataType.LOCALDATETIME)
                .column(f("approved_by"), SQLDataType.VARCHAR(255))
                .column(f("approved_at"), SQLDataType.LOCALDATETIME)
                .constraints(DSL.constraint("pk_" + TABLE).primaryKey(DSL.unquotedName("id")))
                .execute();
    }

    @Override
    public String save(AiGenerationRecord r) {
        dsl.insertInto(table(), f("id"), f("prompt"), f("provider"), f("model"), f("status"),
                f("graph_name"), f("application"), f("candidate"), f("issues"), f("prompt_tokens"),
                f("completion_tokens"), f("repairs"), f("error"), f("created_by"), f("created_at"))
                .values(r.id(), r.prompt(), r.provider(), r.model(), r.status(), r.graph(),
                        r.application(), r.candidate(), r.issues(), r.promptTokens(),
                        r.completionTokens(), r.repairs(), r.error(), r.createdBy(),
                        r.createdAt() == null ? LocalDateTime.now() : r.createdAt())
                .execute();
        return r.id();
    }

    @Override
    public Optional<AiGenerationRecord> find(String id) {
        Record r = dsl.select().from(table()).where(idField().eq(id)).fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    @Override
    public void approve(String id, String approver, String application, String graph) {
        dsl.update(table()).set(f("status"), (Object) AiGenerationRecord.APPROVED)
                .set(f("approved_by"), (Object) approver)
                .set(f("approved_at"), (Object) LocalDateTime.now())
                .set(f("application"), (Object) application).set(f("graph_name"), (Object) graph)
                .where(idField().eq(id)).execute();
    }

    @Override
    public List<AiGenerationRecord> recent(int limit) {
        List<AiGenerationRecord> out = new ArrayList<>();
        for (Record r : dsl.select().from(table())
                .orderBy(DSL.field(DSL.unquotedName("created_at")).desc()).limit(Math.max(0, limit))
                .fetch()) {
            out.add(map(r));
        }
        return out;
    }

    private AiGenerationRecord map(Record r) {
        return new AiGenerationRecord(r.get("id", String.class), r.get("prompt", String.class),
                r.get("provider", String.class), r.get("model", String.class),
                r.get("status", String.class), r.get("graph_name", String.class),
                r.get("application", String.class), r.get("candidate", String.class),
                r.get("issues", String.class), orZero(r.get("prompt_tokens", Integer.class)),
                orZero(r.get("completion_tokens", Integer.class)),
                orZero(r.get("repairs", Integer.class)), r.get("error", String.class),
                r.get("created_by", String.class), r.get("created_at", LocalDateTime.class),
                r.get("approved_by", String.class), r.get("approved_at", LocalDateTime.class));
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static Table<?> table() {
        return DSL.table(DSL.unquotedName(TABLE));
    }

    private static Field<Object> f(String name) {
        return DSL.field(DSL.unquotedName(name));
    }

    private static Field<String> idField() {
        return DSL.field(DSL.unquotedName("id"), String.class);
    }
}
