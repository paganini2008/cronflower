package com.github.cronsmith.springapp.scheduler.jpa;

import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * JPA mapping of {@code cs_task_detail}. Two extra columns over the core schema, {@code bean_name}
 * and {@code application}, carry what the leader needs to dispatch a run to the right executor.
 *
 * @Description: TaskDetailEntity
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "cs_task_detail")
@IdClass(TaskIdEntity.class)
public class TaskDetailEntity {

    @Id
    @Column(name = "task_group", length = 255, nullable = false)
    private String taskGroup;

    @Id
    @Column(name = "task_name", length = 255, nullable = false)
    private String taskName;

    @Column(name = "task_class", length = 255)
    private String taskClass;

    @Column(name = "task_method", length = 255)
    private String taskMethod;

    @Column(name = "bean_name", length = 255)
    private String beanName;

    @Column(name = "application", length = 255)
    private String application;

    @Column(name = "url", length = 1024)
    private String url;

    @Column(name = "description", length = 1024)
    private String description;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "initial_parameter")
    private String initialParameter;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "cron_expression", nullable = false)
    private byte[] cronExpression;

    @Column(name = "cron", length = 255, nullable = false)
    private String cron;

    @Column(name = "next_fired_datetime")
    private LocalDateTime nextFiredDatetime;

    @Column(name = "prev_fired_datetime")
    private LocalDateTime prevFiredDatetime;

    @Column(name = "task_status", length = 45, nullable = false)
    private String taskStatus;

    @Column(name = "misfire_policy", length = 45, nullable = false)
    private String misfirePolicy;

    @Column(name = "max_retry_count")
    private int maxRetryCount;

    @Column(name = "retry_interval")
    private long retryInterval;

    @Column(name = "timeout")
    private long timeout;

    // Boxed on purpose: a database upgraded with ddl-auto=update gains this column as nullable, so
    // rows written before the column existed read back as NULL. A primitive int would throw on such
    // a row; the null is treated as "unlimited" when the task is rebuilt.
    @Column(name = "repeat_count")
    private Integer repeatCount;

    @Column(name = "stop_at")
    private LocalDateTime stopAt;

    @Column(name = "run_count")
    private long runCount;

    @Column(name = "failure_count")
    private long failureCount;

    @Column(name = "misfire_count")
    private long misfireCount;

    @Column(name = "last_modified", nullable = false)
    private LocalDateTime lastModified;

}
