package com.github.cronflow.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * A SCRIPT task reuses the bean-dispatch path (HttpDispatchCustomTask) but carries language/script/
 * datasource instead of a bean+method. These tests pin that the fields survive
 * TaskSaveRequest -> ExecutorTaskMetadata -> the task record -> the DispatchRequest sent to the executor.
 */
class ScriptTaskDispatchTests {

    /** Captures the DispatchRequest the task would send, instead of hitting an executor. */
    static final class CapturingDispatcher implements TaskDispatcher {
        DispatchRequest captured;

        @Override
        public Object dispatchAndWait(DispatchRequest request) {
            this.captured = request;
            return "ok";
        }

        @Override
        public void complete(CompleteRequest result) {
            // no-op for the test
        }
    }

    private TaskSaveRequest scriptRequest() {
        return new TaskSaveRequest("grp", "job", "SCRIPT", null, null, null, "the-param", null, null,
                null, null, "0 0 12 * * ?", "cron", "a script task", 5000L, 0, 0L, "SKIP", 0, null,
                "shell", "echo hi", "ds1");
    }

    @Test
    void saveRequestDetectsScriptAndCarriesFields() {
        TaskSaveRequest req = scriptRequest();
        assertThat(req.isScript()).isTrue();
        assertThat(req.isHttp()).isFalse();
        ExecutorTaskMetadata meta = req.toExecutorMetadata();
        assertThat(meta.isScript()).isTrue();
        assertThat(meta.language()).isEqualTo("shell");
        assertThat(meta.script()).isEqualTo("echo hi");
        assertThat(meta.datasource()).isEqualTo("ds1");
    }

    @Test
    void dispatchCarriesLanguageScriptDatasource() {
        ExecutorTaskMetadata meta = scriptRequest().toExecutorMetadata();
        HttpDispatchCustomTask task = HttpDispatchCustomTask.fromMetadata(meta, "exec-app");
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        TaskDispatcherHolder.set(dispatcher);

        task.invokeTaskMethod(task.getTaskId(), task.getTaskClassName(), task.getTaskMethodName(),
                "the-param");

        DispatchRequest dr = dispatcher.captured;
        assertThat(dr).isNotNull();
        assertThat(dr.application()).isEqualTo("exec-app");
        assertThat(dr.language()).isEqualTo("shell");
        assertThat(dr.script()).isEqualTo("echo hi");
        assertThat(dr.datasource()).isEqualTo("ds1");
        assertThat(dr.initialParameter()).isEqualTo("the-param");
    }

    @Test
    void beanTaskKeepsScriptFieldsNull() {
        ExecutorTaskMetadata meta = new ExecutorTaskMetadata("grp", "job", "com.X", "xBean", "run",
                "0 0 12 * * ?", "cron", "d", "p", 4000L, 2, 1500L, "SKIP", -1, null);
        assertThat(meta.isScript()).isFalse();
        HttpDispatchCustomTask task = HttpDispatchCustomTask.fromMetadata(meta, "app");
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        TaskDispatcherHolder.set(dispatcher);

        task.invokeTaskMethod(task.getTaskId(), task.getTaskClassName(), task.getTaskMethodName(), "p");

        assertThat(dispatcher.captured.language()).isNull();
        assertThat(dispatcher.captured.beanName()).isEqualTo("xBean");
    }
}
