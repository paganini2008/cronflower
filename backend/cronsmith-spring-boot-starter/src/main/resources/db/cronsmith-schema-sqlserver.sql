-- CronSmith task extension schema for SQL Server 2016 and later.
--
-- Mirrors cronsmith-schema-h2.sql; see that file for what the columns mean.
--
-- Note that running against SQL Server needs a commercial JOOQ edition: the open source
-- distribution does not include the SQL Server dialect. The DDL is here so a licensed deployment
-- has it to hand.
--
-- DATETIME2 rather than DATETIME: the older type rounds to about three milliseconds, which is
-- enough to move a stored fire time off the tick it belongs to.

DROP TABLE IF EXISTS cs_task_detail;
CREATE TABLE cs_task_detail (
    task_group          NVARCHAR(255)  NOT NULL,
    task_name           NVARCHAR(255)  NOT NULL,
    task_class          NVARCHAR(255),
    task_method         NVARCHAR(255),
    bean_name           NVARCHAR(255),
    application         NVARCHAR(255),
    url                 NVARCHAR(1024),
    description         NVARCHAR(1024),
    initial_parameter   NVARCHAR(MAX),
    cron_expression     VARBINARY(MAX) NOT NULL,
    cron                NVARCHAR(255)  NOT NULL,
    next_fired_datetime DATETIME2,
    prev_fired_datetime DATETIME2,
    task_status         NVARCHAR(45)   NOT NULL,
    misfire_policy      NVARCHAR(45)   NOT NULL DEFAULT 'FIRE_ONCE_NOW',
    max_retry_count     INT            NOT NULL DEFAULT 0,
    retry_interval      BIGINT         NOT NULL DEFAULT 1000,
    timeout             BIGINT         NOT NULL DEFAULT -1,
    run_count           BIGINT         NOT NULL DEFAULT 0,
    failure_count       BIGINT         NOT NULL DEFAULT 0,
    misfire_count       BIGINT         NOT NULL DEFAULT 0,
    last_modified       DATETIME2      NOT NULL,
    CONSTRAINT pk_cs_task_detail PRIMARY KEY (task_group, task_name)
);

CREATE INDEX idx_cs_task_detail_next_fired ON cs_task_detail (next_fired_datetime);
CREATE INDEX idx_cs_task_detail_status ON cs_task_detail (task_status);

DROP TABLE IF EXISTS cs_task_log;
CREATE TABLE cs_task_log (
    id                  BIGINT         IDENTITY(1,1) PRIMARY KEY,
    task_group          NVARCHAR(255)  NOT NULL,
    task_name           NVARCHAR(255)  NOT NULL,
    scheduled_datetime  DATETIME2      NOT NULL,
    fired_datetime      DATETIME2,
    completed_datetime  DATETIME2,
    return_value        NVARCHAR(MAX),
    error_detail        NVARCHAR(MAX),
    elapsed             BIGINT         NOT NULL DEFAULT 0,
    attempt             INT            NOT NULL DEFAULT 0,
    success             BIT            NOT NULL DEFAULT 0,
    scheduler_repr      VARCHAR(255),
    executor_repr       VARCHAR(255)
);

CREATE INDEX idx_cs_task_log_task ON cs_task_log (task_group, task_name, scheduled_datetime DESC);
