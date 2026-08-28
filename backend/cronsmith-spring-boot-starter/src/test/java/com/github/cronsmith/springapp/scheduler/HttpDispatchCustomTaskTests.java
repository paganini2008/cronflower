package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.cron.CronType;
import com.github.cronsmith.cron.PeriodicCronExpression;
import com.github.cronsmith.springapp.scheduler.MisfirePolicy;

/**
 * Unit tests for the server-side task that dispatches its body to an executor over HTTP. Covers the
 * metadata -> record mapping, the ISO-duration schedule shortcut, the application fallback and the
 * deliberately empty {@code handleResult}.
 *
 * @Description: HttpDispatchCustomTaskTests
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
class HttpDispatchCustomTaskTests {

    private static ExecutorTaskMetadata metadata(String cron) {
        return new ExecutorTaskMetadata("grp", "job", "com.demo.Tasks", "demoTasks", "run", cron,
                "a demo task", "hello", 4000L, 2, 1500L, "SKIP");
    }

    private static ExecutorTaskMetadata metadata(String cron, String parser) {
        return new ExecutorTaskMetadata("grp", "job", "com.demo.Tasks", "demoTasks", "run", cron,
                parser, "a demo task", "hello", 4000L, 2, 1500L, "SKIP");
    }

    @Test
    void fromMetadataMapsEveryField() {
        HttpDispatchCustomTask task =
                HttpDispatchCustomTask.fromMetadata(metadata("0 0 12 * * ?"), "demo-app");

        assertThat(task.getTaskId().getGroup()).isEqualTo("grp");
        assertThat(task.getTaskId().getName()).isEqualTo("job");
        assertThat(task.getTaskClassName()).isEqualTo("com.demo.Tasks");
        assertThat(task.getBeanName()).isEqualTo("demoTasks");
        assertThat(task.getTaskMethodName()).isEqualTo("run");
        assertThat(task.getApplication()).isEqualTo("demo-app");
        assertThat(task.getInitialParameter()).isEqualTo("hello");
        assertThat(task.getTimeout()).isEqualTo(4000L);
        assertThat(task.getMaxRetryCount()).isEqualTo(2);
        assertThat(task.getRetryInterval()).isEqualTo(1500L);
        assertThat(task.getMisfirePolicy()).isEqualTo(MisfirePolicy.SKIP);
    }

    @Test
    void isoDurationScheduleBecomesAFixedPeriod() {
        HttpDispatchCustomTask task =
                HttpDispatchCustomTask.fromMetadata(metadata("PT1H30M"), "demo-app");
        CronExpression cron = task.getCronExpression();
        // An ISO-8601 duration is a true fixed period, not a mangled cron field: 90 min = 5400000 ms.
        assertThat(cron).isInstanceOf(PeriodicCronExpression.class);
        assertThat(cron.toString()).isEqualTo("every 5400000ms");
    }

    @Test
    void ordinaryCronScheduleIsParsedAsCron() {
        HttpDispatchCustomTask task =
                HttpDispatchCustomTask.fromMetadata(metadata("0 0 12 * * ?"), "demo-app");
        CronExpression cron = task.getCronExpression();
        assertThat(cron).isNotInstanceOf(PeriodicCronExpression.class);
        assertThat(cron.getCronType()).isEqualTo(CronType.CRON);
        assertThat(cron.getNextFiredDateTime().getHour()).isEqualTo(12);
    }

    @Test
    void parserYcronParsesTheScheduleAsYearBased() {
        HttpDispatchCustomTask task =
                HttpDispatchCustomTask.fromMetadata(metadata("0 0 12 ? ? 100", "ycron"), "demo-app");
        CronExpression cron = task.getCronExpression();
        // The declared parser reaches AbstractTask, which reads the text as YCRON, not plain cron.
        assertThat(cron.getCronType()).isEqualTo(CronType.YCRON);
        assertThat(cron.toString()).isEqualTo("0 0 12 ? ? 100");
        // ... and once saved as bytes and read back, it is still year-based.
        assertThat(CronExpression.deserialize(cron.serialize()).getCronType())
                .isEqualTo(CronType.YCRON);
    }

    @Test
    void applicationFallsBackToTaskGroupWhenBlank() {
        HttpDispatchCustomTask task =
                HttpDispatchCustomTask.fromMetadata(metadata("0 0 12 * * ?"), "  ");
        assertThat(task.getApplication()).isEqualTo("grp");
    }

    @Test
    void handleResultIsANoOpOnTheServer() {
        HttpDispatchCustomTask task =
                HttpDispatchCustomTask.fromMetadata(metadata("0 0 12 * * ?"), "demo-app");
        // The executor's class is not on the server; this must never try to call back.
        task.handleResult("whatever", new RuntimeException("ignored"));
    }
}
