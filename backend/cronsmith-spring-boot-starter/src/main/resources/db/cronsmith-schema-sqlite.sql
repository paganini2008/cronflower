-- CronSmith task extension schema for SQLite.
--
-- Mirrors cronsmith-schema-h2.sql; see that file for what the columns mean.
--
-- SQLite has no dedicated date type. The declared TIMESTAMP gives the column NUMERIC affinity, and
-- the JDBC driver stores a timestamp as milliseconds since the epoch, which round-trips through
-- JOOQ unchanged. Nothing here should be read as local time.
--
-- Worth knowing before choosing SQLite for this: a write locks the whole database, so a scheduler
-- under load will serialise on it, and there is no safe way to run two nodes against one file.

DROP TABLE IF EXISTS cs_task_detail;
CREATE TABLE cs_task_detail (
    task_group          TEXT          NOT NULL,
    task_name           TEXT          NOT NULL,
    task_class          TEXT,
    task_method         TEXT,
    bean_name           TEXT,
    application         TEXT,
    url                 TEXT,
    description         TEXT,
    initial_parameter   TEXT,
    cron_expression     BLOB          NOT NULL,
    cron                TEXT          NOT NULL,
    next_fired_datetime TIMESTAMP,
    prev_fired_datetime TIMESTAMP,
    task_status         TEXT          NOT NULL,
    misfire_policy      TEXT          NOT NULL DEFAULT 'FIRE_ONCE_NOW',
    max_retry_count     INTEGER       NOT NULL DEFAULT 0,
    retry_interval      INTEGER       NOT NULL DEFAULT 1000,
    timeout             INTEGER       NOT NULL DEFAULT -1,
    run_count           INTEGER       NOT NULL DEFAULT 0,
    failure_count       INTEGER       NOT NULL DEFAULT 0,
    misfire_count       INTEGER       NOT NULL DEFAULT 0,
    last_modified       TIMESTAMP     NOT NULL,
    PRIMARY KEY (task_group, task_name)
);

CREATE INDEX idx_cs_task_detail_next_fired ON cs_task_detail (next_fired_datetime);
CREATE INDEX idx_cs_task_detail_status ON cs_task_detail (task_status);

DROP TABLE IF EXISTS cs_task_log;
CREATE TABLE cs_task_log (
    id                  INTEGER       PRIMARY KEY AUTOINCREMENT,
    task_group          TEXT          NOT NULL,
    task_name           TEXT          NOT NULL,
    scheduled_datetime  TIMESTAMP     NOT NULL,
    fired_datetime      TIMESTAMP,
    completed_datetime  TIMESTAMP,
    return_value        TEXT,
    error_detail        TEXT,
    elapsed             INTEGER       NOT NULL DEFAULT 0,
    attempt             INTEGER       NOT NULL DEFAULT 0,
    success             BOOLEAN       NOT NULL DEFAULT 0,
    scheduler_repr      VARCHAR(255),
    executor_repr       VARCHAR(255)
);

CREATE INDEX idx_cs_task_log_task ON cs_task_log (task_group, task_name, scheduled_datetime DESC);
