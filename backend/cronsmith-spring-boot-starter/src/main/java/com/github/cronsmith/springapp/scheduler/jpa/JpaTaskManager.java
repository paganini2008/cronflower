package com.github.cronsmith.springapp.scheduler.jpa;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.ApiCallTask;
import com.github.cronsmith.springapp.scheduler.BeanReflectionTask;
import com.github.cronsmith.springapp.scheduler.Settings;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskDetailNotFoundException;
import com.github.cronsmith.springapp.scheduler.TaskException;
import com.github.cronsmith.springapp.scheduler.TaskExecutionLog;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskQuery;
import com.github.cronsmith.springapp.scheduler.TaskReflectionUtils;
import com.github.cronsmith.springapp.scheduler.TaskRestoreHandler;
import com.github.cronsmith.springapp.scheduler.TaskStatus;
import com.github.cronsmith.springapp.scheduler.HttpDispatchCustomTask;
import com.github.cronsmith.utils.StringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;

/**
 * A {@link TaskManager} over JPA, mirroring the semantics of the JOOQ implementation: a
 * re-registration refreshes the definition and drops the task to standby without touching its
 * counters, status changes go through a conditional update so racing schedulers cannot both win, and
 * counters are bumped in the database rather than read-modify-written.
 *
 * <p>
 * It is created as a plain object (not a proxied bean), so writes are wrapped in a
 * {@link TransactionTemplate} rather than {@code @Transactional}.
 *
 * @Description: JpaTaskManager
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
public class JpaTaskManager implements TaskManager {

    private static final int MAX_CAS_ATTEMPTS = 8;

    private final EntityManager em;
    private final TransactionTemplate transactionTemplate;

    public JpaTaskManager(EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager) {
        this.em = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public TaskDetail saveTask(Task task, String initialParameter) {
        if (task == null) {
            throw new IllegalArgumentException("Task is required");
        }
        TaskId taskId = task.getTaskId();
        String parameter = StringUtils.isNotBlank(initialParameter) ? initialParameter
                : task.getInitialParameter();
        CronExpression cronExpression = task.getCronExpression().sync();
        String taskClass;
        String taskMethod;
        String url;
        if (task instanceof ApiCallTask apiCallTask) {
            // A data-only HTTP task: no class or method; the request line lives in the url column.
            taskClass = null;
            taskMethod = null;
            url = apiCallTask.getUrl();
        } else if (task instanceof BeanReflectionTask beanReflectionTask) {
            taskClass = beanReflectionTask.getTaskClassName();
            taskMethod = beanReflectionTask.getTaskMethodName();
            url = null;
        } else {
            taskClass = task.getClass().getName();
            taskMethod = Task.DEFAULT_METHOD_NAME;
            url = null;
        }
        String beanName =
                task instanceof HttpDispatchCustomTask ? ((HttpDispatchCustomTask) task).getBeanName()
                        : null;
        String application =
                task instanceof HttpDispatchCustomTask ? ((HttpDispatchCustomTask) task).getApplication()
                        : taskId.getGroup();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                TaskDetailEntity entity =
                        em.find(TaskDetailEntity.class, new TaskIdEntity(taskId.getGroup(),
                                taskId.getName()));
                boolean isNew = entity == null;
                if (isNew) {
                    entity = new TaskDetailEntity();
                    entity.setTaskGroup(taskId.getGroup());
                    entity.setTaskName(taskId.getName());
                    entity.setRunCount(0L);
                    entity.setFailureCount(0L);
                    entity.setMisfireCount(0L);
                }
                entity.setTaskClass(taskClass);
                entity.setTaskMethod(taskMethod);
                entity.setBeanName(beanName);
                entity.setApplication(application);
                entity.setUrl(url);
                entity.setDescription(task.getDescription());
                entity.setCronExpression(cronExpression.serialize());
                entity.setCron(cronExpression.toString());
                entity.setInitialParameter(parameter);
                entity.setMaxRetryCount(task.getMaxRetryCount());
                entity.setRetryInterval(task.getRetryInterval());
                entity.setTimeout(task.getTimeout());
                entity.setMisfirePolicy(task.getMisfirePolicy().name());
                // Re-saving is a re-registration: back to standby with no fire times.
                entity.setTaskStatus(TaskStatus.STANDBY.name());
                entity.setNextFiredDatetime(null);
                entity.setPrevFiredDatetime(null);
                entity.setLastModified(Settings.now());
                if (isNew) {
                    em.persist(entity);
                } else {
                    em.merge(entity);
                }
            });
        } catch (RuntimeException e) {
            throw new TaskException("Cannot save task " + taskId, e);
        }
        return getTaskDetail(taskId, true);
    }

    @Override
    public TaskDetail removeTask(TaskId taskId) {
        TaskDetail taskDetail = getTaskDetail(taskId, false);
        if (taskDetail == null) {
            return null;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                em.createQuery(
                        "delete from TaskLogEntity t where t.taskGroup = :g and t.taskName = :n")
                        .setParameter("g", taskId.getGroup()).setParameter("n", taskId.getName())
                        .executeUpdate();
                em.createQuery(
                        "delete from TaskDetailEntity t where t.taskGroup = :g and t.taskName = :n")
                        .setParameter("g", taskId.getGroup()).setParameter("n", taskId.getName())
                        .executeUpdate();
            });
        } catch (RuntimeException e) {
            throw new TaskException("Cannot remove task " + taskId, e);
        }
        return taskDetail;
    }

    @Override
    public TaskDetail getTaskDetail(TaskId taskId, boolean thrown) {
        TaskDetailEntity entity = taskId != null
                ? em.find(TaskDetailEntity.class, new TaskIdEntity(taskId.getGroup(), taskId.getName()))
                : null;
        if (entity != null) {
            return new RecordTaskDetail(toRecord(entity));
        }
        if (thrown) {
            throw new TaskDetailNotFoundException(taskId);
        }
        return null;
    }

    @Override
    public boolean hasTask(TaskId taskId) {
        if (taskId == null) {
            return false;
        }
        return em.find(TaskDetailEntity.class,
                new TaskIdEntity(taskId.getGroup(), taskId.getName())) != null;
    }

    @Override
    public int getTaskCount(TaskQuery query) {
        Map<String, Object> params = new LinkedHashMap<>();
        String jpql = "select count(t) from TaskDetailEntity t" + whereClause(query, params);
        TypedQuery<Long> typed = em.createQuery(jpql, Long.class);
        params.forEach(typed::setParameter);
        Long count = typed.getSingleResult();
        return count != null ? count.intValue() : 0;
    }

    @Override
    public List<TaskDetail> findTaskDetails(TaskQuery query) {
        Map<String, Object> params = new LinkedHashMap<>();
        String jpql = "select t from TaskDetailEntity t" + whereClause(query, params)
                + " order by t.lastModified desc";
        TypedQuery<TaskDetailEntity> typed = em.createQuery(jpql, TaskDetailEntity.class);
        params.forEach(typed::setParameter);
        if (query != null && query.getOffset() > 0) {
            typed.setFirstResult(query.getOffset());
        }
        if (query != null && query.getLimit() > 0) {
            typed.setMaxResults(query.getLimit());
        }
        List<TaskDetail> details = new ArrayList<>();
        for (TaskDetailEntity entity : typed.getResultList()) {
            details.add(new RecordTaskDetail(toRecord(entity)));
        }
        return details;
    }

    private String whereClause(TaskQuery query, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getTaskGroup())) {
                conditions.add("t.taskGroup = :group");
                params.put("group", query.getTaskGroup());
            }
            if (StringUtils.isNotBlank(query.getTaskName())) {
                conditions.add("t.taskName like :name");
                params.put("name", "%" + query.getTaskName() + "%");
            }
            if (StringUtils.isNotBlank(query.getTaskClass())) {
                conditions.add("t.taskClass like :clazz");
                params.put("clazz", "%" + query.getTaskClass() + "%");
            }
            if (!query.getStatuses().isEmpty()) {
                List<String> names = new ArrayList<>();
                query.getStatuses().forEach(s -> names.add(s.name()));
                conditions.add("t.taskStatus in :statuses");
                params.put("statuses", names);
            }
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    @Override
    public List<LocalDateTime> findNextFiredDateTimes(TaskId taskId, LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        TaskDetailEntity entity =
                em.find(TaskDetailEntity.class, new TaskIdEntity(taskId.getGroup(), taskId.getName()));
        if (entity == null) {
            return List.of();
        }
        TaskStatus status = TaskStatus.forName(entity.getTaskStatus());
        if (status != null && status.isUnavailable()) {
            return List.of();
        }
        return readCronExpression(entity, taskId).list(startDateTime, endDateTime);
    }

    @Override
    public List<TaskId> findUpcomingTasksBetween(LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        List<Object[]> rows = em.createQuery(
                "select t.taskGroup, t.taskName from TaskDetailEntity t where"
                        + " t.nextFiredDatetime >= :s and t.nextFiredDatetime < :e"
                        + " and t.taskStatus not in :ex",
                Object[].class).setParameter("s", startDateTime).setParameter("e", endDateTime)
                .setParameter("ex", List.of(TaskStatus.FINISHED.name(), TaskStatus.CANCELED.name(),
                        TaskStatus.PAUSED.name()))
                .getResultList();
        List<TaskId> ids = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            ids.add(TaskId.of((String) row[0], (String) row[1]));
        }
        return ids;
    }

    @Override
    public LocalDateTime computeNextFiredDateTime(TaskId taskId,
            LocalDateTime previousFiredDateTime) {
        return transactionTemplate.execute(status -> {
            TaskDetailEntity entity = em.find(TaskDetailEntity.class,
                    new TaskIdEntity(taskId.getGroup(), taskId.getName()));
            if (entity == null) {
                return null;
            }
            CronExpression cronExpression = readCronExpression(entity, taskId);
            LocalDateTime nextFiredDateTime =
                    cronExpression.getNextFiredDateTime(previousFiredDateTime);
            entity.setNextFiredDatetime(nextFiredDateTime);
            entity.setPrevFiredDatetime(previousFiredDateTime);
            entity.setCronExpression(cronExpression.serialize());
            entity.setLastModified(Settings.now());
            em.merge(entity);
            return nextFiredDateTime;
        });
    }

    @Override
    public void restoreTasks(TaskRestoreHandler restoreHandler) {
        if (restoreHandler == null) {
            return;
        }
        List<Object[]> rows = em.createQuery(
                "select t.taskGroup, t.taskName, t.nextFiredDatetime from TaskDetailEntity t"
                        + " where t.taskStatus in :statuses",
                Object[].class)
                .setParameter("statuses", List.of(TaskStatus.STANDBY.name(),
                        TaskStatus.SCHEDULED.name(), TaskStatus.RUNNING.name()))
                .getResultList();
        for (Object[] row : rows) {
            TaskId taskId = TaskId.of((String) row[0], (String) row[1]);
            forceStatus(taskId, TaskStatus.STANDBY);
            restoreHandler.onRestore(taskId, (LocalDateTime) row[2]);
        }
    }

    @Override
    public boolean setTaskStatus(TaskId taskId, TaskStatus status) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            TaskStatus current = getTaskStatus(taskId);
            if (current == null || !current.canTransitionTo(status)) {
                return false;
            }
            if (current == status) {
                return true;
            }
            if (casStatus(taskId, current, status)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean compareAndSetTaskStatus(TaskId taskId, TaskStatus expected, TaskStatus target) {
        if (expected == null || target == null || !expected.canTransitionTo(target)) {
            return false;
        }
        return casStatus(taskId, expected, target);
    }

    private boolean casStatus(TaskId taskId, TaskStatus expected, TaskStatus target) {
        Integer updated = transactionTemplate.execute(status -> em.createQuery(
                "update TaskDetailEntity t set t.taskStatus = :target, t.lastModified = :now"
                        + " where t.taskGroup = :g and t.taskName = :n and t.taskStatus = :expected")
                .setParameter("target", target.name()).setParameter("now", Settings.now())
                .setParameter("g", taskId.getGroup()).setParameter("n", taskId.getName())
                .setParameter("expected", expected.name()).executeUpdate());
        return updated != null && updated > 0;
    }

    private void forceStatus(TaskId taskId, TaskStatus target) {
        transactionTemplate.executeWithoutResult(status -> em.createQuery(
                "update TaskDetailEntity t set t.taskStatus = :target, t.lastModified = :now"
                        + " where t.taskGroup = :g and t.taskName = :n")
                .setParameter("target", target.name()).setParameter("now", Settings.now())
                .setParameter("g", taskId.getGroup()).setParameter("n", taskId.getName())
                .executeUpdate());
    }

    @Override
    public void recordExecution(TaskExecutionLog executionLog) {
        if (executionLog == null) {
            return;
        }
        TaskId taskId = executionLog.getTaskId();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                TaskLogEntity log = new TaskLogEntity();
                log.setTaskGroup(taskId.getGroup());
                log.setTaskName(taskId.getName());
                log.setScheduledDatetime(executionLog.getScheduledDateTime());
                log.setFiredDatetime(executionLog.getFiredDateTime());
                log.setCompletedDatetime(executionLog.getCompletedDateTime());
                log.setParameter(executionLog.getParameter());
                log.setReturnValue(executionLog.getReturnValue());
                log.setErrorDetail(executionLog.getErrorDetail());
                log.setElapsed(executionLog.getElapsed());
                log.setAttempt(executionLog.getAttempt());
                log.setSuccess(executionLog.isSuccess());
                log.setSchedulerRepr(executionLog.getSchedulerRepr());
                log.setExecutorRepr(executionLog.getExecutorRepr());
                em.persist(log);
                em.createQuery("update TaskDetailEntity t set t.runCount = t.runCount + 1,"
                        + " t.failureCount = t.failureCount + :failInc, t.lastModified = :now"
                        + " where t.taskGroup = :g and t.taskName = :n")
                        .setParameter("failInc", executionLog.isSuccess() ? 0L : 1L)
                        .setParameter("now", Settings.now()).setParameter("g", taskId.getGroup())
                        .setParameter("n", taskId.getName()).executeUpdate();
            });
        } catch (RuntimeException e) {
            throw new TaskException("Cannot record the execution of task " + taskId, e);
        }
    }

    @Override
    public List<TaskExecutionLog> findExecutionLogs(TaskId taskId, int limit, int offset) {
        TypedQuery<TaskLogEntity> typed = em.createQuery(
                "select t from TaskLogEntity t where t.taskGroup = :g and t.taskName = :n"
                        + " order by t.scheduledDatetime desc, t.attempt desc",
                TaskLogEntity.class).setParameter("g", taskId.getGroup())
                .setParameter("n", taskId.getName());
        if (offset > 0) {
            typed.setFirstResult(offset);
        }
        if (limit > 0) {
            typed.setMaxResults(limit);
        }
        List<TaskExecutionLog> logs = new ArrayList<>();
        for (TaskLogEntity entity : typed.getResultList()) {
            TaskExecutionLog log = new TaskExecutionLog(taskId, entity.getScheduledDatetime())
                    .firedAt(entity.getFiredDatetime()).completedAt(entity.getCompletedDatetime())
                    .elapsed(entity.getElapsed()).attempt(entity.getAttempt())
                    .success(entity.isSuccess()).parameter(entity.getParameter());
            log.setStoredReturnValue(entity.getReturnValue());
            log.setStoredErrorDetail(entity.getErrorDetail());
            log.schedulerRepr(entity.getSchedulerRepr()).executorRepr(entity.getExecutorRepr());
            logs.add(log);
        }
        return logs;
    }

    @Override
    public void recordMisfire(TaskId taskId, LocalDateTime missedDateTime) {
        try {
            transactionTemplate.executeWithoutResult(status -> em.createQuery(
                    "update TaskDetailEntity t set t.misfireCount = t.misfireCount + 1,"
                            + " t.lastModified = :now where t.taskGroup = :g and t.taskName = :n")
                    .setParameter("now", Settings.now()).setParameter("g", taskId.getGroup())
                    .setParameter("n", taskId.getName()).executeUpdate());
        } catch (RuntimeException e) {
            throw new TaskException("Cannot record a misfire of task " + taskId, e);
        }
    }

    private CronExpression readCronExpression(TaskDetailEntity entity, TaskId taskId) {
        byte[] bytes = entity.getCronExpression();
        if (bytes != null && bytes.length > 0) {
            try {
                return CronExpression.deserialize(bytes);
            } catch (RuntimeException e) {
                if (StringUtils.isNotBlank(entity.getCron())) {
                    return CRON.parse(entity.getCron());
                }
                throw new TaskException("Cannot read the schedule of task " + taskId, e);
            }
        }
        if (StringUtils.isNotBlank(entity.getCron())) {
            return CRON.parse(entity.getCron());
        }
        throw new TaskException("No schedule stored for task " + taskId);
    }

    private Map<String, Object> toRecord(TaskDetailEntity e) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskGroup", e.getTaskGroup());
        r.put("taskName", e.getTaskName());
        r.put("taskClass", e.getTaskClass());
        r.put("taskMethod", e.getTaskMethod());
        r.put("beanName", e.getBeanName());
        r.put("application", e.getApplication());
        r.put("url", e.getUrl());
        r.put("description", e.getDescription());
        r.put("initialParameter", e.getInitialParameter());
        r.put("cronExpression", e.getCronExpression());
        r.put("cron", e.getCron());
        r.put("next_fired_datetime", e.getNextFiredDatetime());
        r.put("prev_fired_datetime", e.getPrevFiredDatetime());
        r.put("last_modified", e.getLastModified());
        r.put("taskStatus", e.getTaskStatus());
        r.put("misfirePolicy", e.getMisfirePolicy());
        r.put("maxRetryCount", e.getMaxRetryCount());
        r.put("retryInterval", e.getRetryInterval());
        r.put("timeout", e.getTimeout());
        r.put("runCount", e.getRunCount());
        r.put("failureCount", e.getFailureCount());
        r.put("misfireCount", e.getMisfireCount());
        return r;
    }

}
