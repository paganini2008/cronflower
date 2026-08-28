package com.github.cronsmith.springapp.scheduler.jpa;

import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * JPA mapping of {@code cs_task_log}: one row per attempt.
 *
 * @Description: TaskLogEntity
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "cs_task_log")
public class TaskLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "task_group", length = 255, nullable = false)
    private String taskGroup;

    @Column(name = "task_name", length = 255, nullable = false)
    private String taskName;

    @Column(name = "scheduled_datetime", nullable = false)
    private LocalDateTime scheduledDatetime;

    @Column(name = "fired_datetime")
    private LocalDateTime firedDatetime;

    @Column(name = "completed_datetime")
    private LocalDateTime completedDatetime;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "parameter")
    private String parameter;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "return_value")
    private String returnValue;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "elapsed")
    private long elapsed;

    @Column(name = "attempt")
    private int attempt;

    @Column(name = "success")
    private boolean success;

    /** Who dispatched this run: {@code applicationName,instanceId,host:port}. */
    @Column(name = "scheduler_repr", length = 255)
    private String schedulerRepr;

    /** Who ran this attempt: {@code applicationName,instanceId,host:port}. */
    @Column(name = "executor_repr", length = 255)
    private String executorRepr;

}
