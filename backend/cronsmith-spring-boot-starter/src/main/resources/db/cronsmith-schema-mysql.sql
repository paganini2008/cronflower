-- CronSmith task extension schema for MySQL 8.
--
-- Mirrors cronsmith-schema-h2.sql; the two differ only where the dialects force them to
-- (identity columns, text types, index syntax).
--
-- All timestamps are stored in UTC; see com.github.cronsmith.springapp.scheduler.Settings. DATETIME is used
-- rather than TIMESTAMP because MySQL converts TIMESTAMP values through the session time zone,
-- which would silently shift every stored fire time.

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
    cron_expression     BLOB          NOT NULL,
    cron                VARCHAR(255)  NOT NULL,
    next_fired_datetime DATETIME,
    prev_fired_datetime DATETIME,
    task_status         VARCHAR(45)   NOT NULL,
    misfire_policy      VARCHAR(45)   NOT NULL DEFAULT 'FIRE_ONCE_NOW',
    max_retry_count     INT           NOT NULL DEFAULT 0,
    retry_interval      BIGINT        NOT NULL DEFAULT 1000,
    timeout             BIGINT        NOT NULL DEFAULT -1,
    run_count           BIGINT        NOT NULL DEFAULT 0,
    failure_count       BIGINT        NOT NULL DEFAULT 0,
    misfire_count       BIGINT        NOT NULL DEFAULT 0,
    last_modified       DATETIME      NOT NULL,
    PRIMARY KEY (task_group, task_name),
    KEY idx_cs_task_detail_next_fired (next_fired_datetime),
    KEY idx_cs_task_detail_status (task_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS cs_task_log;
CREATE TABLE cs_task_log (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    task_group          VARCHAR(255)  NOT NULL,
    task_name           VARCHAR(255)  NOT NULL,
    scheduled_datetime  DATETIME      NOT NULL,
    fired_datetime      DATETIME,
    completed_datetime  DATETIME,
    return_value        TEXT,
    error_detail        TEXT,
    elapsed             BIGINT        NOT NULL DEFAULT 0,
    attempt             INT           NOT NULL DEFAULT 0,
    success             TINYINT(1)    NOT NULL DEFAULT 0,
    scheduler_repr      VARCHAR(255),
    executor_repr       VARCHAR(255),
    PRIMARY KEY (id),
    KEY idx_cs_task_log_task (task_group, task_name, scheduled_datetime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
