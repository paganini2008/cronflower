package com.github.cronsmith.springapp.scheduler.jooq;

import java.time.LocalDateTime;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * 
 * The tables the JOOQ task manager reads and writes, named at runtime rather than generated at
 * build time.
 * 
 * <p>
 * Code generation is deliberately avoided here: it would tie the build to a live database or a
 * parsed DDL, and it would fix the table prefix at compile time. A deployment that already owns the
 * {@code cs_} namespace can point this at another prefix instead.
 * 
 * <p>
 * Column names match {@code cronsmith-schema-h2.sql} and {@code cronsmith-schema-mysql.sql}.
 * 
 * @Description: TaskTables
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TaskTables {

    /** The prefix every table carries unless another one is given. */
    public static final String DEFAULT_PREFIX = "cs_";

    // Columns are unqualified: every statement here touches one table, so there is nothing for a
    // qualifier to disambiguate, and leaving them unqualified keeps them shareable.
    //
    // Names are unquoted on purpose. A quoted name is matched exactly, whereas an unquoted one is
    // folded by the dialect's own rule -- upper case on H2 and Oracle, lower case on PostgreSQL.
    // Quoting "task_group" would therefore look for a lower-case column that H2 never created,
    // and the DDL scripts would have to be written in a different case per dialect to match.
    public static final Field<String> TASK_GROUP =
            DSL.field(DSL.unquotedName("task_group"), SQLDataType.VARCHAR);
    public static final Field<String> TASK_NAME =
            DSL.field(DSL.unquotedName("task_name"), SQLDataType.VARCHAR);
    public static final Field<String> TASK_CLASS =
            DSL.field(DSL.unquotedName("task_class"), SQLDataType.VARCHAR);
    public static final Field<String> TASK_METHOD =
            DSL.field(DSL.unquotedName("task_method"), SQLDataType.VARCHAR);
    public static final Field<String> BEAN_NAME =
            DSL.field(DSL.unquotedName("bean_name"), SQLDataType.VARCHAR);
    public static final Field<String> APPLICATION =
            DSL.field(DSL.unquotedName("application"), SQLDataType.VARCHAR);
    public static final Field<String> URL = DSL.field(DSL.unquotedName("url"), SQLDataType.VARCHAR);
    public static final Field<String> DESCRIPTION =
            DSL.field(DSL.unquotedName("description"), SQLDataType.VARCHAR);
    public static final Field<String> INITIAL_PARAMETER =
            DSL.field(DSL.unquotedName("initial_parameter"), SQLDataType.CLOB);
    public static final Field<byte[]> CRON_EXPRESSION =
            DSL.field(DSL.unquotedName("cron_expression"), SQLDataType.BLOB);
    public static final Field<String> CRON = DSL.field(DSL.unquotedName("cron"), SQLDataType.VARCHAR);
    public static final Field<LocalDateTime> NEXT_FIRED_DATETIME =
            DSL.field(DSL.unquotedName("next_fired_datetime"), SQLDataType.LOCALDATETIME);
    public static final Field<LocalDateTime> PREV_FIRED_DATETIME =
            DSL.field(DSL.unquotedName("prev_fired_datetime"), SQLDataType.LOCALDATETIME);
    public static final Field<String> TASK_STATUS =
            DSL.field(DSL.unquotedName("task_status"), SQLDataType.VARCHAR);
    public static final Field<String> MISFIRE_POLICY =
            DSL.field(DSL.unquotedName("misfire_policy"), SQLDataType.VARCHAR);
    public static final Field<Integer> MAX_RETRY_COUNT =
            DSL.field(DSL.unquotedName("max_retry_count"), SQLDataType.INTEGER);
    public static final Field<Long> RETRY_INTERVAL =
            DSL.field(DSL.unquotedName("retry_interval"), SQLDataType.BIGINT);
    public static final Field<Long> TIMEOUT = DSL.field(DSL.unquotedName("timeout"), SQLDataType.BIGINT);
    public static final Field<Integer> REPEAT_COUNT =
            DSL.field(DSL.unquotedName("repeat_count"), SQLDataType.INTEGER);
    public static final Field<LocalDateTime> STOP_AT =
            DSL.field(DSL.unquotedName("stop_at"), SQLDataType.LOCALDATETIME);
    public static final Field<Long> RUN_COUNT =
            DSL.field(DSL.unquotedName("run_count"), SQLDataType.BIGINT);
    public static final Field<Long> FAILURE_COUNT =
            DSL.field(DSL.unquotedName("failure_count"), SQLDataType.BIGINT);
    public static final Field<Long> MISFIRE_COUNT =
            DSL.field(DSL.unquotedName("misfire_count"), SQLDataType.BIGINT);
    public static final Field<LocalDateTime> LAST_MODIFIED =
            DSL.field(DSL.unquotedName("last_modified"), SQLDataType.LOCALDATETIME);

    public static final Field<LocalDateTime> SCHEDULED_DATETIME =
            DSL.field(DSL.unquotedName("scheduled_datetime"), SQLDataType.LOCALDATETIME);
    public static final Field<LocalDateTime> FIRED_DATETIME =
            DSL.field(DSL.unquotedName("fired_datetime"), SQLDataType.LOCALDATETIME);
    public static final Field<LocalDateTime> COMPLETED_DATETIME =
            DSL.field(DSL.unquotedName("completed_datetime"), SQLDataType.LOCALDATETIME);
    public static final Field<String> RETURN_VALUE =
            DSL.field(DSL.unquotedName("return_value"), SQLDataType.CLOB);
    public static final Field<String> ERROR_DETAIL =
            DSL.field(DSL.unquotedName("error_detail"), SQLDataType.CLOB);
    public static final Field<Long> ELAPSED = DSL.field(DSL.unquotedName("elapsed"), SQLDataType.BIGINT);
    public static final Field<Integer> ATTEMPT =
            DSL.field(DSL.unquotedName("attempt"), SQLDataType.INTEGER);
    public static final Field<Boolean> SUCCESS =
            DSL.field(DSL.unquotedName("success"), SQLDataType.BOOLEAN);
    public static final Field<String> SCHEDULER_REPR =
            DSL.field(DSL.unquotedName("scheduler_repr"), SQLDataType.VARCHAR);
    public static final Field<String> EXECUTOR_REPR =
            DSL.field(DSL.unquotedName("executor_repr"), SQLDataType.VARCHAR);

    private final Table<Record> taskDetail;
    private final Table<Record> taskLog;
    private final String prefix;

    public TaskTables() {
        this(DEFAULT_PREFIX);
    }

    public TaskTables(String prefix) {
        this.prefix = prefix != null ? prefix : "";
        this.taskDetail = table(this.prefix + "task_detail");
        this.taskLog = table(this.prefix + "task_log");
    }

    /**
     * The table holding one row per registered task.
     */
    public Table<Record> taskDetail() {
        return taskDetail;
    }

    /**
     * The table holding one row per attempt to run a task.
     */
    public Table<Record> taskLog() {
        return taskLog;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * The task-detail columns, in a typed list. Selecting these rather than a wildcard makes JOOQ
     * bind each value to the Java type declared here -- a LocalDateTime, not the driver's native
     * Timestamp -- so a row reads back in the types the rest of the code expects.
     */
    public Field<?>[] detailFields() {
        return new Field<?>[] {TASK_GROUP, TASK_NAME, TASK_CLASS, TASK_METHOD, BEAN_NAME, APPLICATION,
                URL, DESCRIPTION, INITIAL_PARAMETER, CRON_EXPRESSION, CRON, NEXT_FIRED_DATETIME,
                PREV_FIRED_DATETIME, TASK_STATUS, MISFIRE_POLICY, MAX_RETRY_COUNT, RETRY_INTERVAL,
                TIMEOUT, REPEAT_COUNT, STOP_AT, RUN_COUNT, FAILURE_COUNT, MISFIRE_COUNT,
                LAST_MODIFIED};
    }

    /**
     * The task-log columns, typed for the same reason as {@link #detailFields()}.
     */
    public Field<?>[] logFields() {
        return new Field<?>[] {TASK_GROUP, TASK_NAME, SCHEDULED_DATETIME, FIRED_DATETIME,
                COMPLETED_DATETIME, RETURN_VALUE, ERROR_DETAIL, ELAPSED, ATTEMPT, SUCCESS,
                SCHEDULER_REPR, EXECUTOR_REPR};
    }

    private static Table<Record> table(String name) {
        return DSL.table(DSL.unquotedName(name));
    }

}
