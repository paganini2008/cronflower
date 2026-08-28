package com.github.cronsmith.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Covers task discovery and, above all, {@code resolveSchedule}: exactly one of cron / interval /
 * iso, and the interval-to-ISO conversion.
 */
class TaskRegistryTests {

    private GenericApplicationContext contextWith(Class<?> beanClass) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean("theBean", beanClass);
        ctx.refresh();
        return ctx;
    }

    private TaskMetadata byMethod(List<TaskMetadata> tasks, String methodName) {
        return tasks.stream().filter(t -> t.methodName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("no task for method " + methodName));
    }

    @Test
    void scansCronIntervalAndIso() {
        try (GenericApplicationContext ctx = contextWith(GoodTasks.class)) {
            List<TaskMetadata> tasks = new TaskRegistry(ctx).scan("demo-app");
            assertThat(tasks).hasSize(3);

            // cron passes through verbatim (trimmed).
            assertThat(byMethod(tasks, "cronTask").cron()).isEqualTo("0 0 12 * * ?");
            // interval + unit becomes an ISO-8601 duration string.
            assertThat(byMethod(tasks, "intervalTask").cron()).isEqualTo("PT10S");
            // iso passes through verbatim.
            assertThat(byMethod(tasks, "isoTask").cron()).isEqualTo("PT1H30M");
        }
    }

    @Test
    void extractsFullMetadata() {
        try (GenericApplicationContext ctx = contextWith(GoodTasks.class)) {
            TaskMetadata meta = byMethod(new TaskRegistry(ctx).scan("demo-app"), "cronTask");
            assertThat(meta.taskGroup()).isEqualTo("reports");
            assertThat(meta.taskName()).isEqualTo("nightly");
            assertThat(meta.className()).isEqualTo(GoodTasks.class.getName());
            assertThat(meta.beanName()).isEqualTo("theBean");
            assertThat(meta.methodName()).isEqualTo("cronTask");
            assertThat(meta.description()).isEqualTo("nightly report");
            assertThat(meta.initialParameter()).isEqualTo("p1");
            assertThat(meta.timeout()).isEqualTo(5000L);
            assertThat(meta.maxRetryCount()).isEqualTo(3);
            assertThat(meta.retryInterval()).isEqualTo(2000L);
            assertThat(meta.misfirePolicy()).isEqualTo("SKIP");
        }
    }

    @Test
    void defaultsGroupToApplicationAndNameToBeanDotMethod() {
        try (GenericApplicationContext ctx = contextWith(DefaultingTask.class)) {
            TaskMetadata meta = new TaskRegistry(ctx).scan("demo-app").get(0);
            assertThat(meta.taskGroup()).isEqualTo("demo-app");
            assertThat(meta.taskName()).isEqualTo("theBean.plain");
        }
    }

    @Test
    void rejectsMoreThanOneScheduleKind() {
        try (GenericApplicationContext ctx = contextWith(CronAndInterval.class)) {
            assertThatThrownBy(() -> new TaskRegistry(ctx).scan("demo-app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("exactly one of cron / interval / iso");
        }
    }

    @Test
    void rejectsNoScheduleAtAll() {
        try (GenericApplicationContext ctx = contextWith(NoSchedule.class)) {
            assertThatThrownBy(() -> new TaskRegistry(ctx).scan("demo-app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("exactly one of cron / interval / iso");
        }
    }

    @Test
    void rejectsBadMethodSignature() {
        try (GenericApplicationContext ctx = contextWith(BadSignature.class)) {
            assertThatThrownBy(() -> new TaskRegistry(ctx).scan("demo-app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no arguments or a single String");
        }
    }

    @Test
    void parserDefaultsToCron() {
        try (GenericApplicationContext ctx = contextWith(GoodTasks.class)) {
            assertThat(byMethod(new TaskRegistry(ctx).scan("demo-app"), "cronTask").parser())
                    .isEqualTo("cron");
        }
    }

    @Test
    void parserCarriesYcronThrough() {
        try (GenericApplicationContext ctx = contextWith(YcronTask.class)) {
            assertThat(byMethod(new TaskRegistry(ctx).scan("demo-app"), "yearly").parser())
                    .isEqualTo("ycron");
        }
    }

    @Test
    void parserIsCaseInsensitiveAndTrimmed() {
        try (GenericApplicationContext ctx = contextWith(MessyParser.class)) {
            assertThat(byMethod(new TaskRegistry(ctx).scan("demo-app"), "messy").parser())
                    .isEqualTo("ycron");
        }
    }

    @Test
    void rejectsUnknownParser() {
        try (GenericApplicationContext ctx = contextWith(BadParser.class)) {
            assertThatThrownBy(() -> new TaskRegistry(ctx).scan("demo-app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unknown parser");
        }
    }

    // ---- fixtures ----

    static class GoodTasks {
        @Task(cron = "0 0 12 * * ?", group = "reports", name = "nightly", description = "nightly report",
                initialParameter = "p1", timeout = 5000L, maxRetryCount = 3, retryInterval = 2000L,
                misfirePolicy = "SKIP")
        public void cronTask() {}

        @Task(interval = 10, intervalUnit = TimeUnit.SECONDS)
        public void intervalTask() {}

        @Task(iso = "PT1H30M")
        public void isoTask() {}
    }

    static class DefaultingTask {
        @Task(cron = "0 0 12 * * ?")
        public void plain() {}
    }

    static class CronAndInterval {
        @Task(cron = "0 0 12 * * ?", interval = 5)
        public void bad() {}
    }

    static class NoSchedule {
        @Task
        public void bad() {}
    }

    static class BadSignature {
        @Task(cron = "0 0 12 * * ?")
        public void bad(int notAString) {}
    }

    static class YcronTask {
        @Task(cron = "0 0 12 ? ? 100", parser = "ycron", name = "yearly")
        public void yearly() {}
    }

    static class MessyParser {
        @Task(cron = "0 0 12 ? ? 100", parser = "  YCRON  ", name = "messy")
        public void messy() {}
    }

    static class BadParser {
        @Task(cron = "0 0 12 * * ?", parser = "quartz")
        public void bad() {}
    }

}
