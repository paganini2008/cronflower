package com.github.cronsmith.springapp.scheduler.examples;

import java.time.LocalDateTime;
import com.github.cronsmith.cron.CronBuilder;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskListener;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;

/**
 *
 * A runnable, copy-paste starting point for the stateful task scheduler. It registers one task that
 * fires every second, watches it through a listener, and shuts down cleanly after a few seconds.
 *
 * <p>
 * Run it directly:
 *
 * <pre>
 * mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.github.cronsmith.springapp.scheduler.examples.QuickStartExample
 * </pre>
 *
 * or just run {@link #main(String[])} from your IDE.
 *
 * @Description: QuickStartExample
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class QuickStartExample {

    /**
     *
     * A minimal task: a schedule plus a body. Everything else uses the interface defaults.
     *
     * @Description: HeartbeatTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    static class HeartbeatTask implements Task {

        @Override
        public TaskId getTaskId() {
            return TaskId.of("examples", "heartbeat");
        }

        @Override
        public CronExpression getCronExpression() {
            return new CronBuilder().everySecond(1);
        }

        @Override
        public String getInitialParameter() {
            return "hello";
        }

        @Override
        public Object execute(String initialParameter) {
            System.out.println("  [run]   " + initialParameter + " at " + LocalDateTime.now());
            return "ok";
        }

    }

    public static void main(String[] args) throws InterruptedException {
        // 1. An in-memory scheduler. Nothing to configure for a quick start.
        TimeWheelScheduler scheduler = new TimeWheelScheduler();

        // 2. Watch the task move through its life cycle (optional).
        scheduler.addTaskListener(new TaskListener() {
            @Override
            public void onTaskScheduled(LocalDateTime when, TaskDetail detail) {
                System.out.println("[scheduled] next fire at " + when);
            }

            @Override
            public void onTaskEnded(LocalDateTime when, TaskDetail detail, Object result,
                    Throwable error) {
                System.out.println("  [ended] result=" + (error == null ? result : error));
            }
        });

        // 3. Register the task and start the clock.
        scheduler.schedule(new HeartbeatTask());
        scheduler.start();
        System.out.println("scheduler started; letting it run for 5 seconds...");

        // 4. Let it tick, then read back what happened.
        Thread.sleep(5000L);
        TaskDetail detail =
                scheduler.getTaskManager().getTaskDetail(TaskId.of("examples", "heartbeat"), false);
        System.out.println("total runs recorded: " + detail.getRunCount());

        // 5. Stop the clock. In-flight runs are allowed to finish.
        scheduler.close();
        System.out.println("scheduler closed.");
    }

}
