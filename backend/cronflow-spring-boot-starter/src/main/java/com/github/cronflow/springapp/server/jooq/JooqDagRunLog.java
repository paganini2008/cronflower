package com.github.cronflow.springapp.server.jooq;

import java.sql.Connection;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.tools.jdbc.JDBCUtils;
import com.github.cronflow.springapp.server.DagRunLog;
import com.github.cronflow.springapp.server.DagIds;

/**
 * jOOQ-backed {@link DagRunLog} (cf_dag_log + cf_dag_node_log), the jOOQ counterpart of
 * {@code JpaDagRunLog}. Portable, dialect-agnostic; tables created via {@code createTableIfNotExists}.
 *
 * @Description: JooqDagRunLog
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class JooqDagRunLog implements DagRunLog {

    private static final String RUN = "cf_dag_log";
    private static final String NODE = "cf_dag_node_log";

    private final DSLContext dsl;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(JooqDagRunLog.class);

    public JooqDagRunLog(DataSource dataSource) {
        this.dsl = DSL.using(dataSource, detectDialect(dataSource));
        ensureSchema();
        log.info("cronflow: cf_dag_log / cf_dag_node_log store = jOOQ");
    }

    private static SQLDialect detectDialect(DataSource dataSource) {
        try (Connection c = dataSource.getConnection()) {
            return JDBCUtils.dialect(c);
        } catch (Exception e) {
            throw new IllegalStateException("cronflow: cannot determine SQL dialect", e);
        }
    }

    private void ensureSchema() {
        dsl.createTableIfNotExists(DSL.table(DSL.unquotedName(RUN)))
                .column(DSL.field(DSL.unquotedName("run_id"), SQLDataType.VARCHAR(64).nullable(false)))
                .column(DSL.field(DSL.unquotedName("parent_run_id"), SQLDataType.VARCHAR(64)))
                .column(DSL.field(DSL.unquotedName("application"), SQLDataType.VARCHAR(255)))
                .column(DSL.field(DSL.unquotedName("graph_name"), SQLDataType.VARCHAR(255)))
                .column(DSL.field(DSL.unquotedName("triggered_by"), SQLDataType.VARCHAR(512)))
                .column(DSL.field(DSL.unquotedName("status"), SQLDataType.VARCHAR(16)))
                .column(DSL.field(DSL.unquotedName("started_at"), SQLDataType.LOCALDATETIME))
                .column(DSL.field(DSL.unquotedName("finished_at"), SQLDataType.LOCALDATETIME))
                .column(DSL.field(DSL.unquotedName("elapsed_ms"), SQLDataType.BIGINT))
                .column(DSL.field(DSL.unquotedName("node_count"), SQLDataType.INTEGER))
                .column(DSL.field(DSL.unquotedName("failed_node"), SQLDataType.VARCHAR(255)))
                .column(DSL.field(DSL.unquotedName("input_parameter"), SQLDataType.CLOB))
                .column(DSL.field(DSL.unquotedName("return_value"), SQLDataType.CLOB))
                .column(DSL.field(DSL.unquotedName("error_detail"), SQLDataType.CLOB))
                .constraints(DSL.constraint("pk_" + RUN).primaryKey(DSL.unquotedName("run_id")))
                .execute();
        dsl.createTableIfNotExists(DSL.table(DSL.unquotedName(NODE)))
                .column(DSL.field(DSL.unquotedName("id"), SQLDataType.VARCHAR(96).nullable(false)))
                .column(DSL.field(DSL.unquotedName("run_id"), SQLDataType.VARCHAR(64)))
                .column(DSL.field(DSL.unquotedName("graph_name"), SQLDataType.VARCHAR(255)))
                .column(DSL.field(DSL.unquotedName("node_name"), SQLDataType.VARCHAR(255)))
                .column(DSL.field(DSL.unquotedName("seq"), SQLDataType.INTEGER))
                .column(DSL.field(DSL.unquotedName("status"), SQLDataType.VARCHAR(16)))
                .column(DSL.field(DSL.unquotedName("input_param"), SQLDataType.CLOB))
                .column(DSL.field(DSL.unquotedName("output"), SQLDataType.CLOB))
                .column(DSL.field(DSL.unquotedName("executor"), SQLDataType.VARCHAR(255)))
                .column(DSL.field(DSL.unquotedName("elapsed_ms"), SQLDataType.BIGINT))
                .column(DSL.field(DSL.unquotedName("error_detail"), SQLDataType.CLOB))
                .column(DSL.field(DSL.unquotedName("logged_at"), SQLDataType.LOCALDATETIME))
                .constraints(DSL.constraint("pk_" + NODE).primaryKey(DSL.unquotedName("id")))
                .execute();
    }

    @Override
    public void begin(String runId, String parentRunId, String application, String graph,
            String triggeredBy, LocalDateTime startedAt, String inputParameter) {
        dsl.insertInto(DSL.table(DSL.unquotedName(RUN)),
                DSL.field(DSL.unquotedName("run_id")), DSL.field(DSL.unquotedName("parent_run_id")),
                DSL.field(DSL.unquotedName("application")), DSL.field(DSL.unquotedName("graph_name")),
                DSL.field(DSL.unquotedName("triggered_by")), DSL.field(DSL.unquotedName("status")),
                DSL.field(DSL.unquotedName("started_at")), DSL.field(DSL.unquotedName("input_parameter")))
                .values(runId, parentRunId, application, graph, triggeredBy, "RUNNING", startedAt,
                        inputParameter)
                .execute();
    }

    @Override
    public void finish(String runId, String status, LocalDateTime finishedAt, long elapsedMs,
            int nodeCount, String failedNode, String errorDetail, String returnValue) {
        dsl.update(DSL.table(DSL.unquotedName(RUN)))
                .set(DSL.field(DSL.unquotedName("status")), status)
                .set(DSL.field(DSL.unquotedName("finished_at")), (Object) finishedAt)
                .set(DSL.field(DSL.unquotedName("elapsed_ms")), (Object) elapsedMs)
                .set(DSL.field(DSL.unquotedName("node_count")), (Object) nodeCount)
                .set(DSL.field(DSL.unquotedName("failed_node")), failedNode)
                .set(DSL.field(DSL.unquotedName("error_detail")), errorDetail)
                .set(DSL.field(DSL.unquotedName("return_value")), returnValue)
                .where(DSL.field(DSL.unquotedName("run_id"), String.class).eq(runId))
                .execute();
    }

    @Override
    public void node(String runId, String graph, String node, int seq, String status,
            String inputParam, String output, String executor, long elapsedMs, String errorDetail) {
        dsl.insertInto(DSL.table(DSL.unquotedName(NODE)),
                DSL.field(DSL.unquotedName("id")), DSL.field(DSL.unquotedName("run_id")),
                DSL.field(DSL.unquotedName("graph_name")), DSL.field(DSL.unquotedName("node_name")),
                DSL.field(DSL.unquotedName("seq")), DSL.field(DSL.unquotedName("status")),
                DSL.field(DSL.unquotedName("input_param")), DSL.field(DSL.unquotedName("output")),
                DSL.field(DSL.unquotedName("executor")), DSL.field(DSL.unquotedName("elapsed_ms")),
                DSL.field(DSL.unquotedName("error_detail")), DSL.field(DSL.unquotedName("logged_at")))
                .values(DagIds.nodeLogId(runId, seq), runId, graph, node, seq, status, inputParam,
                        output, executor, elapsedMs, errorDetail, LocalDateTime.now())
                .execute();
    }

}
