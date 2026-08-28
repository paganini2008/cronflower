package com.github.cronsmith.springapp.scheduler.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.github.cronsmith.cron.CronBuilder;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskId;

/**
 * 
 * Task implementations the scheduler tests drive. Each one records what happened to it so a test
 * can assert on the outcome rather than on timing.
 * 
 * @Description: TestTasks
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public abstract class TestTasks {

    /**
     * 
     * Counts its runs and releases a latch, so a test can wait for a definite number of them
     * instead of sleeping and hoping.
     * 
     * @Description: CountingTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class CountingTask implements Task {

        private final String name;
        private final AtomicInteger runs = new AtomicInteger();
        private final List<Object> results = new CopyOnWriteArrayList<>();
        private final List<Throwable> failures = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;

        public CountingTask(String name, int expectedRuns) {
            this.name = name;
            this.latch = new CountDownLatch(expectedRuns);
        }

        @Override
        public TaskId getTaskId() {
            return TaskId.of(name);
        }

        @Override
        public CronExpression getCronExpression() {
            return new CronBuilder().everySecond(1);
        }

        @Override
        public Object execute(String initialParameter) {
            int run = runs.incrementAndGet();
            latch.countDown();
            return name + "-" + run + "-" + initialParameter;
        }

        @Override
        public void handleResult(Object result, Throwable reason) {
            if (result != null) {
                results.add(result);
            }
            if (reason != null) {
                failures.add(reason);
            }
        }

        public int getRuns() {
            return runs.get();
        }

        public List<Object> getResults() {
            return new ArrayList<>(results);
        }

        public List<Throwable> getFailures() {
            return new ArrayList<>(failures);
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

    }

    /**
     * 
     * Fails a fixed number of times before succeeding, which is what the retry path needs.
     * 
     * @Description: FlakyTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class FlakyTask implements Task {

        private final String name;
        private final int failuresBeforeSuccess;
        private final int maxRetryCount;
        private final AtomicInteger attempts = new AtomicInteger();
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Throwable reportedFailure;
        private volatile Object reportedResult;

        public FlakyTask(String name, int failuresBeforeSuccess, int maxRetryCount) {
            this.name = name;
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.maxRetryCount = maxRetryCount;
        }

        @Override
        public TaskId getTaskId() {
            return TaskId.of(name);
        }

        @Override
        public CronExpression getCronExpression() {
            return new CronBuilder().everySecond(1);
        }

        @Override
        public int getMaxRetryCount() {
            return maxRetryCount;
        }

        @Override
        public long getRetryInterval() {
            // Keep the tests quick; the backoff itself is covered by a unit test of its own.
            return 1L;
        }

        @Override
        public Object execute(String initialParameter) {
            int attempt = attempts.incrementAndGet();
            if (attempt <= failuresBeforeSuccess) {
                throw new IllegalStateException("failing attempt " + attempt);
            }
            return "ok after " + attempt;
        }

        @Override
        public void handleResult(Object result, Throwable reason) {
            this.reportedResult = result;
            this.reportedFailure = reason;
            latch.countDown();
        }

        public int getAttempts() {
            return attempts.get();
        }

        public Throwable getReportedFailure() {
            return reportedFailure;
        }

        public Object getReportedResult() {
            return reportedResult;
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

    }

    /**
     * 
     * Blocks for longer than its own timeout, so the timeout path can be observed.
     * 
     * @Description: SlowTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class SlowTask implements Task {

        private final String name;
        private final long sleepMillis;
        private final long timeout;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Throwable reportedFailure;

        public SlowTask(String name, long sleepMillis, long timeout) {
            this.name = name;
            this.sleepMillis = sleepMillis;
            this.timeout = timeout;
        }

        @Override
        public TaskId getTaskId() {
            return TaskId.of(name);
        }

        @Override
        public CronExpression getCronExpression() {
            return new CronBuilder().everySecond(1);
        }

        @Override
        public long getTimeout() {
            return timeout;
        }

        @Override
        public Object execute(String initialParameter) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "done";
        }

        @Override
        public void handleResult(Object result, Throwable reason) {
            this.reportedFailure = reason;
            latch.countDown();
        }

        public Throwable getReportedFailure() {
            return reportedFailure;
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

    }

    /**
     * 
     * Runs once and never again, so the transition into FINISHED can be observed.
     * 
     * @Description: OneShotTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class OneShotTask implements Task {

        private final String name;
        private final CronExpression cronExpression;
        private final AtomicInteger runs = new AtomicInteger();

        public OneShotTask(String name, CronExpression cronExpression) {
            this.name = name;
            this.cronExpression = cronExpression;
        }

        @Override
        public TaskId getTaskId() {
            return TaskId.of(name);
        }

        @Override
        public CronExpression getCronExpression() {
            return cronExpression;
        }

        @Override
        public Object execute(String initialParameter) {
            return runs.incrementAndGet();
        }

        public int getRuns() {
            return runs.get();
        }

    }

    /**
     * 
     * A task whose whole definition is data, so it survives a round trip through the database: it
     * is stored as a row and rebuilt from that row by the default custom task factory, which is how
     * a persisted schedule actually works. The body is reached reflectively on {@link
     * ReflectiveTarget}.
     * 
     * @Description: PersistentTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class PersistentTask extends com.github.cronsmith.springapp.scheduler.BeanReflectionTask {

        public PersistentTask(String group, String name, String cronText) {
            this(group, name, cronText, "execute");
        }

        public PersistentTask(String group, String name, String cronText, String method) {
            super(record(group, name, cronText, method));
        }

        private static java.util.Map<String, Object> record(String group, String name,
                String cronText, String method) {
            java.util.Map<String, Object> record = new java.util.HashMap<>();
            record.put("taskGroup", group);
            record.put("taskName", name);
            record.put("taskClass", ReflectiveTarget.class.getName());
            record.put("taskMethod", method);
            record.put("cron", cronText);
            return record;
        }

        @Override
        protected Object invokeTaskMethod(TaskId taskId, String taskClassName, String taskMethodName,
                String initialParameter) {
            // Not called on the restored path -- there the rebuilt bean-reflection task runs the body
            // reflectively -- but a task saved directly still needs a working execute().
            return new ReflectiveTarget().execute(initialParameter);
        }

    }

    /**
     * 
     * A plain class with no schedule of its own, reached reflectively the way a task restored from
     * a database row is.
     * 
     * @Description: ReflectiveTarget
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class ReflectiveTarget {

        private static final AtomicInteger CALLS = new AtomicInteger();

        public String execute(String parameter) {
            CALLS.incrementAndGet();
            return "reflective:" + parameter;
        }

        public String other(String parameter) {
            return "other:" + parameter;
        }

        String packagePrivate(String parameter) {
            return "package:" + parameter;
        }

        public static int getCalls() {
            return CALLS.get();
        }

        public static void resetCalls() {
            CALLS.set(0);
        }

    }

}
