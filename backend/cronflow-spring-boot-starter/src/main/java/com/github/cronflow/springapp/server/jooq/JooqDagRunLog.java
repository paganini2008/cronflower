package com.github.cronflow.springapp.server.jooq;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.tools.jdbc.JDBCUtils;
import com.github.cronflow.springapp.server.DagRunLog;
import com.github.cronflow.springapp.server.pojo.DagRunView;
import com.github.cronflow.springapp.server.pojo.DagNodeView;
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

    // ---- read side ------------------------------------------------------------------------------
    // Typed field constants (as cronsmith's TaskTables): SELECTing these makes jOOQ apply each column's
    // data-type binding when reading from JDBC, so a datetime comes back as LocalDateTime on every
    // dialect (SQLite returns a java.sql.Timestamp for an untyped read, which would then fail to cast).

    private static Field<String> vc(String n) {
        return DSL.field(DSL.unquotedName(n), SQLDataType.VARCHAR);
    }

    private static Field<String> clob(String n) {
        return DSL.field(DSL.unquotedName(n), SQLDataType.CLOB);
    }

    private static Field<LocalDateTime> ts(String n) {
        return DSL.field(DSL.unquotedName(n), SQLDataType.LOCALDATETIME);
    }

    private static final Field<String> RUN_ID = vc("run_id");
    private static final Field<String> PARENT_RUN_ID = vc("parent_run_id");
    private static final Field<String> APPLICATION = vc("application");
    private static final Field<String> GRAPH_NAME = vc("graph_name");
    private static final Field<String> TRIGGERED_BY = vc("triggered_by");
    private static final Field<String> RUN_STATUS = vc("status");
    private static final Field<LocalDateTime> STARTED_AT = ts("started_at");
    private static final Field<LocalDateTime> FINISHED_AT = ts("finished_at");
    private static final Field<Long> ELAPSED_MS = DSL.field(DSL.unquotedName("elapsed_ms"), SQLDataType.BIGINT);
    private static final Field<Integer> NODE_COUNT = DSL.field(DSL.unquotedName("node_count"), SQLDataType.INTEGER);
    private static final Field<String> FAILED_NODE = vc("failed_node");
    private static final Field<String> INPUT_PARAMETER = clob("input_parameter");
    private static final Field<String> RETURN_VALUE = clob("return_value");
    private static final Field<String> ERROR_DETAIL = clob("error_detail");

    private static final Field<String> NODE_NAME = vc("node_name");
    private static final Field<Integer> SEQ = DSL.field(DSL.unquotedName("seq"), SQLDataType.INTEGER);
    private static final Field<String> INPUT_PARAM = clob("input_param");
    private static final Field<String> OUTPUT = clob("output");
    private static final Field<String> EXECUTOR = vc("executor");
    private static final Field<LocalDateTime> LOGGED_AT = ts("logged_at");

    private static final Field<?>[] RUN_COLS = {RUN_ID, PARENT_RUN_ID, APPLICATION, GRAPH_NAME,
            TRIGGERED_BY, RUN_STATUS, STARTED_AT, FINISHED_AT, ELAPSED_MS, NODE_COUNT, FAILED_NODE,
            INPUT_PARAMETER, RETURN_VALUE, ERROR_DETAIL};
    private static final Field<?>[] NODE_COLS = {RUN_ID, GRAPH_NAME, NODE_NAME, SEQ, RUN_STATUS,
            INPUT_PARAM, OUTPUT, EXECUTOR, ELAPSED_MS, ERROR_DETAIL, LOGGED_AT};

    @Override
    public List<DagRunView> listRuns(String application, String graph, String status, int limit,
            int offset) {
        List<DagRunView> out = new ArrayList<>();
        for (Record r : dsl.select(RUN_COLS).from(DSL.table(DSL.unquotedName(RUN)))
                .where(runFilters(application, graph, status)).orderBy(STARTED_AT.desc())
                .limit(Math.max(1, limit)).offset(Math.max(0, offset)).fetch()) {
            out.add(toRunView(r));
        }
        return out;
    }

    @Override
    public int countRuns(String application, String graph, String status) {
        Integer n = dsl.selectCount().from(DSL.table(DSL.unquotedName(RUN)))
                .where(runFilters(application, graph, status)).fetchOne(0, Integer.class);
        return n == null ? 0 : n;
    }

    @Override
    public DagRunView findRun(String runId) {
        Record r = dsl.select(RUN_COLS).from(DSL.table(DSL.unquotedName(RUN)))
                .where(RUN_ID.eq(runId)).fetchOne();
        return r == null ? null : toRunView(r);
    }

    @Override
    public List<DagNodeView> nodesOf(String runId) {
        List<DagNodeView> out = new ArrayList<>();
        for (Record r : dsl.select(NODE_COLS).from(DSL.table(DSL.unquotedName(NODE)))
                .where(RUN_ID.eq(runId)).orderBy(SEQ.asc()).fetch()) {
            out.add(toNodeView(r));
        }
        return out;
    }

    @Override
    public List<DagRunView> childRuns(String parentRunId) {
        List<DagRunView> out = new ArrayList<>();
        for (Record r : dsl.select(RUN_COLS).from(DSL.table(DSL.unquotedName(RUN)))
                .where(PARENT_RUN_ID.eq(parentRunId)).orderBy(STARTED_AT.desc()).fetch()) {
            out.add(toRunView(r));
        }
        return out;
    }

    private static List<Condition> runFilters(String application, String graph, String status) {
        List<Condition> c = new ArrayList<>();
        if (isSet(application)) {
            c.add(APPLICATION.eq(application));
        }
        if (isSet(graph)) {
            c.add(GRAPH_NAME.eq(graph));
        }
        if (isSet(status)) {
            c.add(RUN_STATUS.eq(status));
        }
        return c;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static DagRunView toRunView(Record r) {
        return new DagRunView(r.get(RUN_ID), r.get(PARENT_RUN_ID), r.get(APPLICATION),
                r.get(GRAPH_NAME), r.get(TRIGGERED_BY), r.get(RUN_STATUS), r.get(STARTED_AT),
                r.get(FINISHED_AT), r.get(ELAPSED_MS), r.get(NODE_COUNT), r.get(FAILED_NODE),
                r.get(INPUT_PARAMETER), r.get(RETURN_VALUE), r.get(ERROR_DETAIL));
    }

    private static DagNodeView toNodeView(Record r) {
        return new DagNodeView(r.get(RUN_ID), r.get(GRAPH_NAME), r.get(NODE_NAME), r.get(SEQ),
                r.get(RUN_STATUS), r.get(INPUT_PARAM), r.get(OUTPUT), r.get(EXECUTOR),
                r.get(ELAPSED_MS), r.get(ERROR_DETAIL), r.get(LOGGED_AT));
    }

}
