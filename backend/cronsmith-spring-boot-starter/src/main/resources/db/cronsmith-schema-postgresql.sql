-- CronSmith task extension schema for PostgreSQL.
--
-- Mirrors cronsmith-schema-h2.sql; see that file for what the columns mean.
-- All timestamps are stored in UTC, so TIMESTAMP WITHOUT TIME ZONE is what is wanted here:
-- TIMESTAMPTZ would convert through the session time zone on the way in and out.

DROP TABLE IF EXISTS cs_task_detail;
CREATE TABLE cs_task_detail (
    task_group          VARCHAR(255)  NOT NULL,
    task_name           VARCHAR(255)  NOT NULL,
    task_class          VARCHAR(255),
    task_method         VARCHAR(255),
    bean_name           VARCHAR(255),
    application         VARCHAR(255),
    url                 VARCHAR(1024),
    description         VARCHAR(1024),
    initial_parameter   TEXT,
    cron_expression     BYTEA         NOT NULL,
    cron                VARCHAR(255)  NOT NULL,
    next_fired_datetime TIMESTAMP,
    prev_fired_datetime TIMESTAMP,
    task_status         VARCHAR(45)   NOT NULL,
    misfire_policy      VARCHAR(45)   NOT NULL DEFAULT 'FIRE_ONCE_NOW',
    max_retry_count     INTEGER       NOT NULL DEFAULT 0,
    retry_interval      BIGINT        NOT NULL DEFAULT 1000,
    timeout             BIGINT        NOT NULL DEFAULT -1,
    -- Periodic-task limits: repeat_count <= 0 means unlimited; stop_at NULL means no deadline.
    repeat_count        INTEGER       NOT NULL DEFAULT -1,
    stop_at             TIMESTAMP,
    run_count           BIGINT        NOT NULL DEFAULT 0,
    failure_count       BIGINT        NOT NULL DEFAULT 0,
    misfire_count       BIGINT        NOT NULL DEFAULT 0,
    last_modified       TIMESTAMP     NOT NULL,
    PRIMARY KEY (task_group, task_name)
);

CREATE INDEX idx_cs_task_detail_next_fired ON cs_task_detail (next_fired_datetime);
CREATE INDEX idx_cs_task_detail_status ON cs_task_detail (task_status);

DROP TABLE IF EXISTS cs_task_log;
CREATE TABLE cs_task_log (
    id                  BIGSERIAL     PRIMARY KEY,
    task_group          VARCHAR(255)  NOT NULL,
    task_name           VARCHAR(255)  NOT NULL,
    scheduled_datetime  TIMESTAMP     NOT NULL,
    fired_datetime      TIMESTAMP,
    completed_datetime  TIMESTAMP,
    return_value        TEXT,
    error_detail        TEXT,
    elapsed             BIGINT        NOT NULL DEFAULT 0,
    attempt             INTEGER       NOT NULL DEFAULT 0,
    success             BOOLEAN       NOT NULL DEFAULT FALSE,
    scheduler_repr      VARCHAR(255),
    executor_repr       VARCHAR(255)
);

CREATE INDEX idx_cs_task_log_task ON cs_task_log (task_group, task_name, scheduled_datetime DESC);
