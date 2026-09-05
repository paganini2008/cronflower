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

    @Test
    void repeatCountDefaultsToUnlimitedAndNoDeadline() {
        try (GenericApplicationContext ctx = contextWith(GoodTasks.class)) {
            TaskMetadata meta = byMethod(new TaskRegistry(ctx).scan("demo-app"), "cronTask");
            assertThat(meta.repeatCount()).isEqualTo(-1);
            assertThat(meta.stopAt()).isNull();
        }
    }

    @Test
    void repeatCountCarriesFromAnnotation() {
        try (GenericApplicationContext ctx = contextWith(RepeatingTask.class)) {
            TaskMetadata meta = byMethod(new TaskRegistry(ctx).scan("demo-app"), "capped");
            assertThat(meta.repeatCount()).isEqualTo(5);
            // stopAt has no annotation attribute, so it stays null without a builder.
            assertThat(meta.stopAt()).isNull();
        }
    }

    @Test
    void builderOverridesCronParserRepeatAndStopAt() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean("theBean", BuiltTask.class);
        ctx.registerBean("myBuilder", DemoBuilder.class);
        ctx.refresh();
        try (ctx) {
            TaskMetadata meta = byMethod(new TaskRegistry(ctx).scan("demo-app"), "built");
            // The builder wins over the annotation's own cron / parser.
            assertThat(meta.cron()).isEqualTo("0 0 9 ? * MON");
            assertThat(meta.parser()).isEqualTo("ycron");
            assertThat(meta.repeatCount()).isEqualTo(4);
            assertThat(meta.stopAt()).isEqualTo(DemoBuilder.STOP_AT.toString());
        }
    }

    @Test
    void rejectsBuilderThatIsNotACronExpressionBuilder() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean("theBean", BuiltTask.class);
        // "myBuilder" is missing entirely.
        ctx.refresh();
        try (ctx) {
            assertThatThrownBy(() -> new TaskRegistry(ctx).scan("demo-app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not a CronExpressionBuilder bean");
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

    static class RepeatingTask {
        @Task(interval = 10, intervalUnit = TimeUnit.SECONDS, repeatCount = 5, name = "capped")
        public void capped() {}
    }

    static class BuiltTask {
        // A named builder wins, so this cron / parser are deliberately the wrong answer.
        @Task(builder = "myBuilder", cron = "0 0 12 * * ?", parser = "cron", name = "built")
        public void built() {}
    }

    static class DemoBuilder implements CronExpressionBuilder {
        static final java.time.LocalDateTime STOP_AT =
                java.time.LocalDateTime.of(2027, 1, 1, 0, 0, 0);

        @Override
        public String buildCron() {
            return "0 0 9 ? * MON";
        }

        @Override
        public String getParser() {
            return "ycron";
        }

        @Override
        public int getRepeatCount() {
            return 4;
        }

        @Override
        public java.time.LocalDateTime getStopAt() {
            return STOP_AT;
        }
    }

}
